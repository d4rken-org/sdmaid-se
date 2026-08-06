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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
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

    // Test seam: same reason, the cooldown between failed probe rounds is a production sized wait.
    internal var probeFailureCooldown: Duration = PROBE_FAILURE_COOLDOWN

    // Test seam: the launch duration is wall-clock measured, so a virtual-time test cannot reach
    // the production bound either.
    internal var reviewMinDuration: Duration = REVIEW_MIN_DURATION

    // Test seam: a hung Play call would otherwise have to be waited out for the production bound.
    internal var requestTimeout: Duration = REQUEST_TIMEOUT

    // Test seam: same reason, the review sheet bound is minutes long.
    internal var launchTimeout: Duration = LAUNCH_TIMEOUT

    // Test seam: eligibility is a function of wall-clock time, a virtual-time test needs to be able
    // to move "now" itself. Only used for eligibility, the persisted timestamps stay real.
    internal var nowProvider: () -> Instant = Instant::now

    // Local bookkeeping only: decided without talking to Play, so an ineligible user never
    // triggers a Play round-trip.
    private val isLocallyEligible: Flow<Boolean> = combine(
        settings.lastDismissed.flow,
        settings.reviewedAt.flow,
        upgradeRepo.upgradeInfo,
    ) { lastDismissed, reviewedAt, upgradeInfo ->
        EligibilityInputs(
            lastDismissed = lastDismissed,
            reviewedAt = reviewedAt,
            upgradedAt = upgradeInfo.upgradedAt,
        )
    }
        // Eligibility depends on time, not just on the inputs: a snooze that runs out while the
        // process is alive has to flip the verdict, otherwise the card only reappears after a
        // restart. Re-evaluated at the upcoming boundaries, an input change restarts the whole
        // schedule via flatMapLatest.
        .flatMapLatest { inputs ->
            flow {
                var wakes = 0
                while (true) {
                    val now = nowProvider()
                    emit(inputs.isEligibleAt(now))

                    val nextBoundary = inputs.boundariesAfter(now).minOrNull()
                    if (nextBoundary == null || wakes == MAX_BOUNDARY_WAKES) break

                    wakes++
                    val wakeIn = Duration.between(now, nextBoundary) + BOUNDARY_WAKE_MARGIN
                    log(TAG) { "Eligibility: Re-evaluating in $wakeIn (boundary $nextBoundary)" }
                    delay(wakeIn.toMillis())
                }
            }
        }
        .distinctUntilChanged()
        // Upstream of the shares below: an exception there would kill the sharing coroutine on
        // AppScope (crashing the process) instead of reaching any downstream `catch`.
        .catch { e ->
            if (e is CancellationException) throw e
            log(TAG, ERROR) { "Eligibility failed: ${e.asLog()}" }
            emit(false)
        }

    // Play's definitive answers are worth exactly one round-trip per process: quota is limited and
    // the verdict doesn't change while the app runs. Survives eligibility flips, unlike the share.
    @Volatile private var cachedVerdict: Verdict? = null

    // Process-wide on purpose: the eligibility flatMapLatest below restarts this branch on every
    // input change (a billing flicker flipping upgradedAt is enough), and a restart must not hand
    // out a fresh round budget.
    @Volatile private var probeRoundsStarted: Int = 0

    // Only probed once the user is eligible: Play counts requests against the app's quota, and an
    // `isNoOp` answer is Play's deliberate verdict, i.e. an answer and not a failure to retry.
    // `null` means no probe ran (not eligible), which is not a Play answer and is never cached.
    private val reviewAvailability: Flow<Verdict?> = isLocallyEligible
        .flatMapLatest { eligible ->
            if (!eligible) return@flatMapLatest flowOf<Verdict?>(null)

            flow {
                cachedVerdict?.let {
                    log(TAG) { "Reusing cached probe verdict: $it" }
                    emit(it)
                    return@flow
                }

                while (true) {
                    if (probeRoundsStarted == FAILURE_RETRY_ROUNDS + 1) {
                        log(TAG, WARN) { "Probe failed in all ${FAILURE_RETRY_ROUNDS + 1} rounds, giving up" }
                        emit(Verdict.TRANSIENT_FAILURE)
                        return@flow
                    }
                    if (probeRoundsStarted > 0) delay(probeFailureCooldown.toMillis())

                    val round = probeRoundsStarted
                    probeRoundsStarted++

                    val verdict = probeRound(round)
                    emit(verdict)

                    if (verdict != Verdict.TRANSIENT_FAILURE) {
                        // Play answered, that answer holds for the rest of the process
                        cachedVerdict = verdict
                        return@flow
                    }
                }
            }
        }
        // Lazily, not WhileSubscribed: a re-subscription must not spend another Play request on a
        // question that was already answered (or given up on) in this process.
        .shareIn(appScope, SharingStarted.Lazily, replay = 1)

    private suspend fun probeRound(round: Int): Verdict {
        for (attempt in 1..PROBE_ATTEMPTS) {
            val info = try {
                // withTimeoutOrNull, never withTimeout: TimeoutCancellationException IS-A
                // CancellationException and would escape through the rethrow below.
                withTimeoutOrNull(requestTimeout.toMillis()) { manager.requestReview() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Probe $round:$attempt/$PROBE_ATTEMPTS failed: ${e.asLog()}" }
                if (attempt < PROBE_ATTEMPTS) delay(probeRetryDelay.toMillis())
                continue
            }

            if (info == null) {
                // Our timeout does not cancel the underlying Play Task: an immediate retry would
                // stack concurrent quota-consuming requests against an already hung service, so the
                // whole round is abandoned. The orphaned Task is tolerated, its completion resumes
                // a cancelled continuation and is discarded.
                log(TAG, WARN) { "Probe $round:$attempt/$PROBE_ATTEMPTS timed out, abandoning the round" }
                return Verdict.TRANSIENT_FAILURE
            }

            log(TAG) { "Probe $round:$attempt/$PROBE_ATTEMPTS returned ${info.desc()}" }
            return if (info.canShow) Verdict.AVAILABLE else Verdict.UNAVAILABLE_BY_PLAY
        }
        log(TAG, WARN) { "Probe round $round gave up after $PROBE_ATTEMPTS attempts" }
        return Verdict.TRANSIENT_FAILURE
    }

    override val state: Flow<ReviewTool.State> = combine(
        isLocallyEligible,
        reviewAvailability,
        settings.reviewedAt.flow,
    ) { eligible, verdict, reviewedAt ->
        log(TAG) { "State: eligible=$eligible, verdict=$verdict, reviewedAt=$reviewedAt" }
        ReviewTool.State(
            shouldAskForReview = eligible && verdict == Verdict.AVAILABLE,
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

    // Tap race backstop: a dismiss can land while a reviewNow is waiting on Play. In-memory on
    // purpose, re-reading the persisted timestamp would race the write it is supposed to observe.
    @Volatile private var dismissGeneration: Int = 0

    override suspend fun dismiss() {
        log(TAG, INFO) { "dismiss()" }
        // Bumped before the write: an in-flight reviewNow has to see the dismiss immediately,
        // not once DataStore has persisted it.
        dismissGeneration++
        settings.lastDismissed.value(Instant.now())
    }

    override suspend fun reviewNow(activity: Activity) {
        log(TAG, INFO) { "reviewNow($activity)" }

        if (!reviewLock.tryLock()) {
            log(TAG, WARN) { "reviewNow(...) is already in progress, skipping" }
            return
        }

        try {
            val generation = dismissGeneration

            // ReviewInfo is short lived, Google wants it requested shortly before the launch,
            // a token cached at process start is likely stale by the time the user taps.
            val reviewInfo = try {
                withTimeoutOrNull(requestTimeout.toMillis()) { manager.requestReview() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A transient failure is not user intent: don't snooze the card, the next tap retries
                log(TAG, ERROR) { "Failed to get a fresh ReviewInfo: ${e.asLog()}" }
                return
            }

            if (reviewInfo == null) {
                // A hang is not user intent either: persist nothing, the next tap retries
                log(TAG, ERROR) { "Timed out requesting a fresh ReviewInfo" }
                return
            }
            log(TAG) { "reviewNow(...): Fresh ${reviewInfo.desc()}" }

            if (dismissGeneration != generation) {
                log(TAG, WARN) { "Dismissed while we were requesting a fresh ReviewInfo, aborting" }
                return
            }

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

            if (dismissGeneration != generation) {
                log(TAG, WARN) { "Dismissed before we could launch, aborting" }
                return
            }

            val reviewTime = measureTimeMillis {
                val launched = try {
                    // withTimeoutOrNull for the same reason as the probe: a withTimeout would be
                    // rethrown as cancellation by the branch below.
                    withTimeoutOrNull(launchTimeout.toMillis()) { manager.launchReview(activity, reviewInfo) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to launch review flow: ${e.asLog()}" }
                    return
                }

                if (launched == null) {
                    // Far beyond any real review session: the sheet's Task is hung and must not hold
                    // the single-flight lock forever. The outcome is unknown, so nothing is persisted
                    // and the duration heuristic below is skipped.
                    log(TAG, WARN) { "Timed out waiting for the review flow to finish" }
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

    private data class EligibilityInputs(
        val lastDismissed: Instant?,
        val reviewedAt: Instant?,
        val upgradedAt: Instant?,
    )

    private fun EligibilityInputs.isEligibleAt(now: Instant): Boolean {
        // Free trial is 14 days, only ask for review after the user has paid something
        val hasPaidForPro = Duration.between(upgradedAt ?: now, now) > PRO_GRACE_PERIOD
        val isSnoozed = Duration.between(lastDismissed ?: Instant.EPOCH, now) < SNOOZE_PERIOD
        val hasReviewed = reviewedAt != null

        log(TAG) { "Eligibility: hasPaidForPro=$hasPaidForPro ($upgradedAt)" }
        log(TAG) { "Eligibility: isSnoozed=$isSnoozed ($lastDismissed), hasReviewed=$hasReviewed ($reviewedAt)" }

        return hasPaidForPro && !isSnoozed && !hasReviewed
    }

    private fun EligibilityInputs.boundariesAfter(now: Instant): List<Instant> = listOfNotNull(
        lastDismissed?.plus(SNOOZE_PERIOD),
        upgradedAt?.plus(PRO_GRACE_PERIOD),
    ).filter { it > now }

    private val ReviewInfo.canShow: Boolean
        get() = when {
            toString().contains("isNoOp=true") -> false
            else -> true
        }

    private fun ReviewInfo.desc(): String {
        return "ReviewInfo(canShow=$canShow, ${toString()})"
    }

    private enum class Verdict {
        // Play answered with a usable ReviewInfo
        AVAILABLE,

        // Play answered with an isNoOp ReviewInfo, i.e. a deliberate "no"
        UNAVAILABLE_BY_PLAY,

        // No answer: attempts exhausted by exceptions, or the round was abandoned on a timeout
        TRANSIENT_FAILURE,
    }

    companion object {
        private val TAG = logTag("Review", "Tool", "Gplay")
        private const val PROBE_ATTEMPTS = 3
        private const val FAILURE_RETRY_ROUNDS = 3
        private val PROBE_RETRY_DELAY = Duration.ofSeconds(30)
        private val PROBE_FAILURE_COOLDOWN = Duration.ofMinutes(15)
        private val REVIEW_MIN_DURATION = Duration.ofSeconds(2)
        private val REQUEST_TIMEOUT = Duration.ofSeconds(10)
        private val LAUNCH_TIMEOUT = Duration.ofMinutes(10)
        private val SNOOZE_PERIOD = Duration.ofDays(14)
        private val PRO_GRACE_PERIOD = Duration.ofDays(21)
        private const val MAX_BOUNDARY_WAKES = 4
        private val BOUNDARY_WAKE_MARGIN = Duration.ofSeconds(1)
    }
}
