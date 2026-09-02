package eu.darken.sdmse.common.upgrade.core.billing.work

import androidx.work.ListenableWorker.Result
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager.AckSweepResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class PurchaseAckWorkerTest : BaseTest() {

    @Test fun `sweeping is only worth it before the refund deadline`() {
        PurchaseAckWorker.isWorthSweeping(now = 100L, expiresAt = 101L) shouldBe true
        PurchaseAckWorker.isWorthSweeping(now = 100L, expiresAt = 100L) shouldBe false
        PurchaseAckWorker.isWorthSweeping(now = 100L, expiresAt = 99L) shouldBe false
        // Malformed input data (missing/zero deadline) must not retry forever.
        PurchaseAckWorker.isWorthSweeping(now = 100L, expiresAt = 0L) shouldBe false
    }

    @Test fun `a complete sweep succeeds`() {
        PurchaseAckWorker.mapSweep(AckSweepResult.COMPLETE, now = 100L, expiresAt = 200L)
            .shouldBeInstanceOf<Result.Success>()
    }

    @Test fun `a permanently rejected ack stops the retries`() {
        PurchaseAckWorker.mapSweep(AckSweepResult.PERMANENT_FAILURE, now = 100L, expiresAt = 200L)
            .shouldBeInstanceOf<Result.Failure>()
    }

    @Test fun `transient outcomes retry until the deadline`() {
        PurchaseAckWorker.mapSweep(AckSweepResult.RETRY, now = 100L, expiresAt = 200L)
            .shouldBeInstanceOf<Result.Retry>()
        // null = the sweep timed out: same transient treatment.
        PurchaseAckWorker.mapSweep(null, now = 100L, expiresAt = 200L)
            .shouldBeInstanceOf<Result.Retry>()
        // Past the deadline Play has already refunded: give up visibly.
        PurchaseAckWorker.mapSweep(AckSweepResult.RETRY, now = 200L, expiresAt = 200L)
            .shouldBeInstanceOf<Result.Failure>()
        PurchaseAckWorker.mapSweep(null, now = 200L, expiresAt = 200L)
            .shouldBeInstanceOf<Result.Failure>()
    }

    @Test fun `a periodic sweep that completed succeeds`() {
        PurchaseAckWorker.mapPeriodicSweep(AckSweepResult.COMPLETE, runAttemptCount = 0)
            .shouldBeInstanceOf<Result.Success>()
    }

    @Test fun `a permanently rejected periodic ack stops the retries`() {
        PurchaseAckWorker.mapPeriodicSweep(AckSweepResult.PERMANENT_FAILURE, runAttemptCount = 0)
            .shouldBeInstanceOf<Result.Failure>()
    }

    @Test fun `transient periodic outcomes retry within the period, then wait for the next one`() {
        PurchaseAckWorker.mapPeriodicSweep(AckSweepResult.RETRY, runAttemptCount = 0)
            .shouldBeInstanceOf<Result.Retry>()
        PurchaseAckWorker.mapPeriodicSweep(null, runAttemptCount = 0)
            .shouldBeInstanceOf<Result.Retry>()
        PurchaseAckWorker.mapPeriodicSweep(AckSweepResult.RETRY, runAttemptCount = 1)
            .shouldBeInstanceOf<Result.Retry>()
        PurchaseAckWorker.mapPeriodicSweep(null, runAttemptCount = 1)
            .shouldBeInstanceOf<Result.Retry>()
        // Attempts exhausted: the next period is the retry, a Play outage must not chain backoff
        // retries across the whole interval.
        PurchaseAckWorker.mapPeriodicSweep(AckSweepResult.RETRY, runAttemptCount = 2)
            .shouldBeInstanceOf<Result.Failure>()
        PurchaseAckWorker.mapPeriodicSweep(null, runAttemptCount = 2)
            .shouldBeInstanceOf<Result.Failure>()
    }

    @Test fun `work without a mode keeps the deadline gate`() = runTest2 {
        var swept = false
        val result = PurchaseAckWorker.run(
            mode = null,
            expiresAt = 0L,
            runAttemptCount = 0,
            now = { 100L },
        ) {
            swept = true
            AckSweepResult.COMPLETE
        }

        result.shouldBeInstanceOf<Result.Failure>()
        swept shouldBe false
    }

    @Test fun `deadline work sweeps and maps by deadline`() = runTest2 {
        var swept = false
        val result = PurchaseAckWorker.run(
            mode = PurchaseAckWorker.MODE_DEADLINE,
            expiresAt = 200L,
            runAttemptCount = 0,
            now = { 100L },
        ) {
            swept = true
            AckSweepResult.RETRY
        }

        swept shouldBe true
        result.shouldBeInstanceOf<Result.Retry>()
    }

    @Test fun `periodic work sweeps without a deadline`() = runTest2 {
        var swept = false
        val result = PurchaseAckWorker.run(
            mode = PurchaseAckWorker.MODE_PERIODIC,
            expiresAt = 0L,
            runAttemptCount = 0,
            now = { 100L },
        ) {
            swept = true
            AckSweepResult.COMPLETE
        }

        swept shouldBe true
        result.shouldBeInstanceOf<Result.Success>()
    }
}
