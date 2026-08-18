package eu.darken.sdmse.common.upgrade.core.billing.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Persistent acknowledgement safety net, armed by [PurchaseAckScheduler].
 *
 * Play auto-refunds (and revokes) any purchase not acknowledged within 3 days. The in-process ack
 * machinery in [BillingManager] handles every case where the process lives long enough — this
 * worker covers the case it can't: the process dies around the Play purchase sheet (OEM task
 * killers) and the user doesn't reopen the app before the deadline. Play voids such purchases and
 * revokes the entitlement, so the user loses what they signed up for.
 *
 * Self-completing by design: nothing cancels this work from the foreground ack path (an ack pass
 * can legitimately see zero unacknowledged purchases while the Play sheet is still open, which
 * must not tear down the net). The redundant sweep after a successful foreground ack is one
 * purchase query.
 */
@HiltWorker
class PurchaseAckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val billingManager: BillingManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val expiresAt = inputData.getLong(KEY_EXPIRES_AT, 0L)
        log(TAG) { "doWork(): attempt=$runAttemptCount, expiresAt=$expiresAt" }

        if (!isWorthSweeping(System.currentTimeMillis(), expiresAt)) {
            // Past Play's refund deadline (or malformed input): retrying can't achieve anything.
            // failure() is deliberate over success() — it is visible in WorkManager diagnostics,
            // and a completed state lets a later KEEP enqueue insert fresh work.
            log(TAG, WARN) { "doWork(): deadline passed, giving up" }
            return Result.failure()
        }

        // Bounded well below WorkManager's 10-minute execution limit, but generous enough for the
        // connection wait plus the per-purchase inline retries. A sweep that ran out of time is a
        // transient outcome, not a verdict. External cancellation propagates out of doWork — it
        // must never be converted into success.
        val sweep = withTimeoutOrNull(SWEEP_TIMEOUT_MS) {
            billingManager.ensureAllAcknowledged()
        }
        log(TAG, INFO) { "doWork(): sweep=$sweep" }

        return mapSweep(sweep, System.currentTimeMillis(), expiresAt)
    }

    companion object {
        // Persisted in WorkManager's request data — keep the key stable while old work may exist.
        const val KEY_EXPIRES_AT = "purchase.ack.expiresAt"

        private const val SWEEP_TIMEOUT_MS = 4 * 60 * 1000L

        // Pure so the retry/expiry decision is unit-testable without a WorkManager test harness.
        internal fun isWorthSweeping(now: Long, expiresAt: Long): Boolean =
            expiresAt > 0L && now < expiresAt

        internal fun mapSweep(
            sweep: BillingManager.AckSweepResult?,
            now: Long,
            expiresAt: Long,
        ): Result = when (sweep) {
            BillingManager.AckSweepResult.COMPLETE -> Result.success()
            BillingManager.AckSweepResult.PERMANENT_FAILURE -> Result.failure()
            // RETRY or timeout (null): keep trying until the deadline. WorkManager's exponential
            // backoff caps at 5h, so the 3-day window still yields many attempts.
            else -> if (now < expiresAt) Result.retry() else Result.failure()
        }

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "AckWorker")
    }
}
