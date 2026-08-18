package eu.darken.sdmse.common.upgrade.core.billing.work

import androidx.work.ListenableWorker.Result
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager.AckSweepResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

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
}
