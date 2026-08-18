package eu.darken.sdmse.common.upgrade.core.billing.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Arms the [PurchaseAckWorker] safety net. Two triggers:
 * - a billing flow is about to launch (armed and awaited BEFORE the Play sheet, so the WorkManager
 *   DB transaction lands even if the process dies around the sheet),
 * - an ack pass discovered unacknowledged purchases (called directly, pre-attempt, from
 *   BillingManager's runAckPass).
 */
@Singleton
class PurchaseAckScheduler @Inject constructor(
    // Resolved on the first arm, not at construction: fleet App classes eagerly inject the billing
    // stack during Application field injection, and resolving WorkManager there can trigger its
    // on-demand initialization before the Application's worker factory field is set.
    private val workManager: Provider<WorkManager>,
) {

    // A genuinely new flow refreshes the watch window: REPLACE the previous LAUNCH watch. The
    // worker sweeps ALL unacknowledged purchases, so replacing an older watch loses nothing — and a
    // pending rescue for an already-discovered purchase has its own identity, so starting another
    // purchase can never displace it. The long delay keeps the worker out of the window where the
    // user may still be in the Play sheet.
    suspend fun armForBillingFlowLaunch() = arm(
        name = WORK_NAME_LAUNCH,
        policy = ExistingWorkPolicy.REPLACE,
        expiresAt = System.currentTimeMillis() + BillingManager.ACK_SAFETY_NET_DEADLINE_MS,
        initialDelayMs = LAUNCH_DELAY_MS,
    )

    // Any pending rescue already covers every unacknowledged purchase: KEEP it. Once completed
    // work exists, KEEP inserts a fresh request. Short delay — the purchase already EXISTS (unlike
    // the launch trigger), possibly for days, so waiting 30min could waste real deadline time.
    // Accepted edge of KEEP, within the rescue lane only: a pending request keeps its original
    // (possibly earlier) expiry; a newer purchase with a later deadline is only re-covered once a
    // later pass re-arms after the old work completed. Bounded residual, only reachable via
    // out-of-band purchases.
    suspend fun armForUnackedPurchases(expiresAt: Long) = arm(
        name = WORK_NAME_RESCUE,
        policy = ExistingWorkPolicy.KEEP,
        expiresAt = expiresAt,
        initialDelayMs = DISCOVERY_DELAY_MS,
    )

    private suspend fun arm(
        name: String,
        policy: ExistingWorkPolicy,
        expiresAt: Long,
        initialDelayMs: Long,
    ) {
        if (expiresAt <= System.currentTimeMillis()) {
            // Play has already voided (or is about to void) such a purchase; a sweep can't help.
            log(TAG, WARN) { "arm($policy): deadline $expiresAt already passed, not scheduling" }
            return
        }
        val request = OneTimeWorkRequestBuilder<PurchaseAckWorker>().apply {
            setConstraints(
                Constraints.Builder().apply {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }.build()
            )
            // Launch trigger: the worker must not run while the user may still be in the Play
            // sheet — an immediate sweep would find nothing unacknowledged, report success, and
            // complete the net before the purchase it exists for even happened.
            setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MS, TimeUnit.MILLISECONDS)
            setInputData(workDataOf(PurchaseAckWorker.KEY_EXPIRES_AT to expiresAt))
        }.build()

        // Await the enqueue: the caller arms this because the process may die at any moment — a
        // fire-and-forget enqueue could be lost with it. Cancellable and BOUNDED: every caller
        // needs a durable enqueue without an unbounded stall — a WorkManager that never settles
        // must become an exception (handled fail-open by every caller) instead of a hang (which on
        // the launch lane would park the purchase and its busy guard forever).
        val operation = workManager.get().enqueueUniqueWork(name, policy, request)
        withTimeoutOrNull(ENQUEUE_TIMEOUT_MS) { operation.await() }
            ?: throw IllegalStateException("WorkManager enqueue did not settle within ${ENQUEUE_TIMEOUT_MS}ms")
        log(TAG) { "arm($policy): safety net armed, expiresAt=$expiresAt" }
    }

    companion object {
        // WorkManager persists these names AND the worker's class name in its DB across app
        // updates: keep all of them stable while old work may exist (hence the version suffix for
        // future changes). Separate identities per trigger: the launch watch's REPLACE must not be
        // able to displace a pending rescue for a purchase that already exists.
        private val WORK_NAME_LAUNCH = "${BuildConfigWrap.APPLICATION_ID}.gplay.purchase-ack.launch.v1"
        private val WORK_NAME_RESCUE = "${BuildConfigWrap.APPLICATION_ID}.gplay.purchase-ack.rescue.v1"

        private const val LAUNCH_DELAY_MS = 30 * 60 * 1000L
        private const val DISCOVERY_DELAY_MS = 60 * 1000L
        private const val BACKOFF_DELAY_MS = 30 * 60 * 1000L
        private const val ENQUEUE_TIMEOUT_MS = 10 * 1000L

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "AckScheduler")
    }
}
