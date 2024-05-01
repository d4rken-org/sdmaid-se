package eu.darken.sdmse.common.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
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
import eu.darken.sdmse.main.core.release.ReleaseSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class GplayReviewTool @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val settings: ReviewSettings,
    releaseSettings: ReleaseSettings,
    upgradeRepo: UpgradeRepo,
) : ReviewTool {
    private val manager by lazy { ReviewManagerFactory.create(context) }
    private val reviewRefresh = MutableStateFlow(UUID.randomUUID())
    private val gplayReviewState = reviewRefresh
        .map {
            try {
                manager.requestReview().also {
                    log(TAG) { "requestReview(): ${it.desc()}" }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to get ReviewInfo: ${e.asLog()}" }
                null
            }
        }
        .replayingShare(appScope)

    override val state: Flow<ReviewTool.State> = combine(
        settings.lastDismissed.flow,
        settings.reviewedAt.flow,
        gplayReviewState,
        upgradeRepo.upgradeInfo,
        releaseSettings.releasePartyAt.flow,
    ) { lastDismissed, reviewedAt, reviewInfo, upgradeInfo, releasePartyAt ->
        val now = Instant.now()
        val isSnoozed = Duration.between(lastDismissed ?: Instant.EPOCH, now) < Duration.ofDays(14)
        val canShow = reviewInfo?.canShow == true
        val hasReviewed = reviewedAt != null

        // Free trial is 14 days, only ask for review after the user has paid something
        val hasPaidForPro = Duration.between(upgradeInfo.upgradedAt ?: now, now) > Duration.ofDays(21)

        // User may still be hangover from party, don't ask for review
        val hasRecoveredFromParty = Duration.between(releasePartyAt ?: now, now) > Duration.ofDays(5)

        log(TAG) { "State 1: canShow=$canShow, isSnoozed=$isSnoozed ($lastDismissed), reviewedAt=$reviewedAt" }
        log(TAG) { "State 2: hasRecoveredFromParty=$hasRecoveredFromParty, hasPaidForPro=$hasPaidForPro" }

        ReviewTool.State(
            shouldAskForReview = hasRecoveredFromParty && hasPaidForPro && !isSnoozed && !hasReviewed && canShow,
            hasReviewed = hasReviewed,
        )
    }
        .throttleLatest(500)
        .onStart { emit(ReviewTool.State()) }
        .replayingShare(appScope)

    override suspend fun dismiss() {
        log(TAG, INFO) { "dismiss()" }
        settings.lastDismissed.value(Instant.now())
    }

    override suspend fun reviewNow(activity: Activity) {
        val reviewInfo = gplayReviewState.first()
        log(TAG, INFO) { "reviewNow($activity, ${reviewInfo?.desc()})" }

        if (reviewInfo == null) {
            log(TAG, WARN) { "ReviewInfo is unavailable" }
            return
        }

        if (!reviewInfo.canShow) {
            log(TAG, ERROR) { "ReviewInfo says we can't show the prompt, how did we get here?" }
            return
        }

        val reviewTime = measureTimeMillis {
            manager.launchReview(activity, reviewInfo)
        }
        log(TAG) { "Review completed after ${reviewTime}ms" }
        reviewRefresh.value = UUID.randomUUID()

        if (Duration.ofMillis(reviewTime) >= Duration.ofSeconds(2)) {
            log(TAG, INFO) { "Marking review as completed" }
            settings.reviewedAt.value(Instant.now())
        } else {
            log(TAG, INFO) { "Review was too quick, counting as dismiss" }
            settings.lastDismissed.value(Instant.now())
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
    }
}