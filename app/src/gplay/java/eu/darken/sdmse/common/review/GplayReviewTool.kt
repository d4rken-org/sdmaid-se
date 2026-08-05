package eu.darken.sdmse.common.review

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class GplayReviewTool @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val settings: ReviewSettings,
    private val manager: ReviewManager,
    upgradeRepo: UpgradeRepo,
) : ReviewTool {

    // Test seam: the probe backoff runs on AppScope (a real dispatcher), so a virtual-time test
    // cannot advance the production bound. Same pattern as UpgradeRepoGplay.launchTimeoutMs.
    internal var probeRetryDelay: Duration = PROBE_RETRY_DELAY

    // Test seam: the launch duration is wall-clock measured, so a virtual-time test cannot reach
    // the production bound either.
    internal var reviewMinDuration: Duration = REVIEW_MIN_DURATION

    // Local bookkeeping only: decided without talking to Play, so an ineligible user never
    // triggers a Play round-trip.
    private val isLocallyEligible: Flow<Boolean> = combine(
        settings.lastDismissed.flow,
        settings.reviewedAt.flow,
        upgradeRepo.upgradeInfo,
    ) { lastDismissed, reviewedAt, upgradeInfo ->
        val now = Instant.now()

        // Free trial is 14 days, only ask for review after the user has paid something
        val hasPaidForPro = Duration.between(upgradeInfo.upgradedAt ?: now, now) > Duration.ofDays(21)
        val isSnoozed = Duration.between(lastDismissed ?: Instant.EPOCH, now) < Duration.ofDays(14)
        val hasReviewed = reviewedAt != null

        log(TAG) { "Eligibility: hasPaidForPro=$hasPaidForPro (${upgradeInfo.upgradedAt})" }
        log(TAG) { "Eligibility: isSnoozed=$isSnoozed ($lastDismissed), hasReviewed=$hasReviewed ($reviewedAt)" }

        hasPaidForPro && !isSnoozed && !hasReviewed
    }
        .distinctUntilChanged()
        // Upstream of the shares below: an exception there would kill the sharing coroutine on
        // AppScope (crashing the process) instead of reaching any downstream `catch`.
        .catch { e ->
            if (e is CancellationException) throw e
            log(TAG, ERROR) { "Eligibility failed: ${e.asLog()}" }
            emit(false)
        }

    // Only probed once the user is eligible: Play counts requests against the app's quota, and an
    // `isNoOp` answer is Play's deliberate verdict, i.e. an answer and not a failure to retry.
    private val isReviewAvailable: Flow<Boolean> = isLocallyEligible
        .flatMapLatest { eligible ->
            if (!eligible) return@flatMapLatest flowOf(false)

            flow {
                for (attempt in 1..PROBE_ATTEMPTS) {
                    val info = try {
                        manager.requestReview()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Probe $attempt/$PROBE_ATTEMPTS failed: ${e.asLog()}" }
                        if (attempt < PROBE_ATTEMPTS) delay(probeRetryDelay.toMillis())
                        continue
                    }
                    log(TAG) { "Probe $attempt/$PROBE_ATTEMPTS returned ${info.desc()}" }
                    emit(info.canShow)
                    return@flow
                }
                // Re-probed when eligibility changes or on the next process start
                log(TAG, WARN) { "Probe gave up after $PROBE_ATTEMPTS attempts" }
                emit(false)
            }
        }
        .replayingShare(appScope)

    override val state: Flow<ReviewTool.State> = combine(
        isLocallyEligible,
        isReviewAvailable,
        settings.reviewedAt.flow,
    ) { eligible, available, reviewedAt ->
        log(TAG) { "State: eligible=$eligible, available=$available, reviewedAt=$reviewedAt" }
        ReviewTool.State(
            shouldAskForReview = eligible && available,
            hasReviewed = reviewedAt != null,
        )
    }
        .throttleLatest(500)
        .onStart { emit(ReviewTool.State()) }
        .catch { e ->
            if (e is CancellationException) throw e
            log(TAG, ERROR) { "State failed: ${e.asLog()}" }
            emit(ReviewTool.State())
        }
        .replayingShare(appScope)

    // Single-flight: a second tap must not queue up behind the first, or Play's flow would be
    // launched again the moment the user returns from it.
    private val reviewLock = Mutex()

    override suspend fun dismiss() {
        log(TAG, INFO) { "dismiss()" }
        settings.lastDismissed.value(Instant.now())
    }

    override suspend fun reviewNow(activity: Activity) {
        log(TAG, INFO) { "reviewNow($activity)" }

        if (!reviewLock.tryLock()) {
            log(TAG, WARN) { "reviewNow(...) is already in progress, skipping" }
            return
        }

        try {
            // ReviewInfo is short lived, Google wants it requested shortly before the launch,
            // a token cached at process start is likely stale by the time the user taps.
            val reviewInfo = try {
                manager.requestReview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A transient failure is not user intent: don't snooze the card, the next tap retries
                log(TAG, ERROR) { "Failed to get a fresh ReviewInfo: ${e.asLog()}" }
                return
            }
            log(TAG) { "reviewNow(...): Fresh ${reviewInfo.desc()}" }

            if (!reviewInfo.canShow) {
                // Play's quota verdict, asking again right away would be pointless
                log(TAG, WARN) { "Play says we can't show the prompt, snoozing" }
                settings.lastDismissed.value(Instant.now())
                return
            }

            if (activity.isFinishing || activity.isDestroyed) {
                log(TAG, WARN) { "Activity is gone, aborting: $activity" }
                return
            }

            val reviewTime = measureTimeMillis {
                try {
                    manager.launchReview(activity, reviewInfo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to launch review flow: ${e.asLog()}" }
                    return
                }
            }
            log(TAG) { "Review completed after ${reviewTime}ms" }

            if (Duration.ofMillis(reviewTime) >= reviewMinDuration) {
                log(TAG, INFO) { "Marking review as completed" }
                settings.reviewedAt.value(Instant.now())
            } else {
                log(TAG, INFO) { "Review was too quick, counting as dismiss" }
                settings.lastDismissed.value(Instant.now())
            }
        } finally {
            reviewLock.unlock()
        }
    }

    private val ReviewInfo.canShow: Boolean
        get() = when {
            toString().contains("isNoOp=true") -> false
            else -> true
        }

    private fun ReviewInfo.desc(): String {
        return "ReviewInfo(canShow=$canShow, ${toString()})"
    }

    companion object {
        private val TAG = logTag("Review", "Tool", "Gplay")
        private const val PROBE_ATTEMPTS = 3
        private val PROBE_RETRY_DELAY = Duration.ofSeconds(30)
        private val REVIEW_MIN_DURATION = Duration.ofSeconds(2)
    }
}
