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
        val mode = inputData.getString(KEY_MODE)
        log(TAG) { "doWork(): attempt=$runAttemptCount, mode=$mode, expiresAt=$expiresAt" }

        return run(
            mode = mode,
            expiresAt = expiresAt,
            runAttemptCount = runAttemptCount,
            now = System::currentTimeMillis,
        ) {
            // Bounded well below WorkManager's 10-minute execution limit, but generous enough for
            // the connection wait plus the per-purchase inline retries. A sweep that ran out of
            // time is a transient outcome, not a verdict. External cancellation propagates out of
            // doWork — it must never be converted into success.
            withTimeoutOrNull(SWEEP_TIMEOUT_MS) {
                billingManager.ensureAllAcknowledged()
            }
        }
    }

    companion object {
        // Persisted in WorkManager's request data — keep the key stable while old work may exist.
        const val KEY_EXPIRES_AT = "purchase.ack.expiresAt"

        // Also persisted: absent means MODE_DEADLINE, so requests enqueued by older builds keep
        // their behaviour.
        const val KEY_MODE = "purchase.ack.mode"
        const val MODE_DEADLINE = "deadline"
        const val MODE_PERIODIC = "periodic"

        private const val SWEEP_TIMEOUT_MS = 4 * 60 * 1000L

        private const val PERIODIC_MAX_ATTEMPTS = 3

        // Pure so the routing is unit-testable without a WorkerParameters harness.
        internal suspend fun run(
            mode: String?,
            expiresAt: Long,
            runAttemptCount: Int,
            now: () -> Long,
            sweep: suspend () -> BillingManager.AckSweepResult?,
        ): Result {
            if (mode == MODE_PERIODIC) {
                val result = sweep()
                log(TAG, INFO) { "doWork(): sweep=$result" }
                val exhausted = result != BillingManager.AckSweepResult.COMPLETE &&
                    result != BillingManager.AckSweepResult.PERMANENT_FAILURE &&
                    runAttemptCount + 1 >= PERIODIC_MAX_ATTEMPTS
                if (exhausted) {
                    log(TAG, WARN) {
                        "Periodic ack sweep gave up for this period after $PERIODIC_MAX_ATTEMPTS attempts"
                    }
                }
                return mapPeriodicSweep(result, runAttemptCount)
            }

            if (!isWorthSweeping(now(), expiresAt)) {
                // Past Play's refund deadline (or malformed input): retrying can't achieve anything.
                // failure() is deliberate over success() — it is visible in WorkManager diagnostics,
                // and a completed state lets a later KEEP enqueue insert fresh work.
                log(TAG, WARN) { "doWork(): deadline passed, giving up" }
                return Result.failure()
            }

            val result = sweep()
            log(TAG, INFO) { "doWork(): sweep=$result" }

            return mapSweep(result, now(), expiresAt)
        }

        // Pure so the retry/expiry decision is unit-testable without a WorkManager test harness.
        internal fun isWorthSweeping(now: Long, expiresAt: Long): Boolean =
            expiresAt > 0L && now < expiresAt

        // For periodic work WorkManager treats success and failure identically for scheduling: both
        // reset the request to ENQUEUED for the next period and reset the attempt count (2.11,
        // WorkerWrapper.handleResult), only its own result log line differs. Retries within a period
        // are bounded so a Play outage can't chain backoff retries across the whole interval — the
        // next period is the retry.
        internal fun mapPeriodicSweep(
            sweep: BillingManager.AckSweepResult?,
            runAttemptCount: Int,
        ): Result = when (sweep) {
            BillingManager.AckSweepResult.COMPLETE -> Result.success()
            BillingManager.AckSweepResult.PERMANENT_FAILURE -> Result.failure()
            else -> if (runAttemptCount + 1 < PERIODIC_MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

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
