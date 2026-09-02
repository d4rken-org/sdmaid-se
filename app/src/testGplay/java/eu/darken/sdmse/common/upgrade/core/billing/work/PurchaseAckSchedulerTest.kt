package eu.darken.sdmse.common.upgrade.core.billing.work

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import javax.inject.Provider

class PurchaseAckSchedulerTest : BaseTest() {

    // The enqueue is awaited: hand back an already-settled future so await() takes its fast path.
    private val enqueueFuture = mockk<ListenableFuture<Operation.State.SUCCESS>>().apply {
        every { isDone } returns true
        every { get() } returns mockk()
    }
    private val operation = mockk<Operation>().apply {
        every { result } returns enqueueFuture
    }
    private val workManager = mockk<WorkManager>().apply {
        every {
            enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } returns operation
        every {
            enqueueUniquePeriodicWork(any<String>(), any<ExistingPeriodicWorkPolicy>(), any<PeriodicWorkRequest>())
        } returns operation
    }

    private fun create() = PurchaseAckScheduler(
        workManager = Provider { workManager },
    )

    @Test fun `a billing flow launch arms the launch watch`() = runTest2 {
        create().armForBillingFlowLaunch()

        val name = slot<String>()
        val policy = slot<ExistingWorkPolicy>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(capture(name), capture(policy), any<OneTimeWorkRequest>())
        }
        name.captured shouldEndWith ".gplay.purchase-ack.launch.v1"
        policy.captured shouldBe ExistingWorkPolicy.REPLACE
    }

    @Test fun `discovered unacknowledged purchases arm the rescue lane`() = runTest2 {
        create().armForUnackedPurchases(expiresAt = System.currentTimeMillis() + 60 * 1000L)

        val name = slot<String>()
        val policy = slot<ExistingWorkPolicy>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(capture(name), capture(policy), any<OneTimeWorkRequest>())
        }
        // A separate identity from the launch watch: a new purchase flow must not displace a
        // pending rescue for a purchase that already exists.
        name.captured shouldEndWith ".gplay.purchase-ack.rescue.v1"
        policy.captured shouldBe ExistingWorkPolicy.KEEP
    }

    @Test fun `a process start arms the periodic sweep`() = runTest2 {
        create().armPeriodicSweep()

        val name = slot<String>()
        val policy = slot<ExistingPeriodicWorkPolicy>()
        val request = slot<PeriodicWorkRequest>()
        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(capture(name), capture(policy), capture(request))
        }
        name.captured shouldEndWith ".gplay.purchase-ack.periodic"
        // UPDATE, not KEEP: periodic work never completes, so KEEP would ignore every later request
        // and a changed spec would silently never reach the device.
        policy.captured shouldBe ExistingPeriodicWorkPolicy.UPDATE

        val spec = request.captured.workSpec
        spec.input.getString(PurchaseAckWorker.KEY_MODE) shouldBe PurchaseAckWorker.MODE_PERIODIC
        spec.intervalDuration shouldBe 12 * 60 * 60 * 1000L
        spec.constraints.requiredNetworkType shouldBe NetworkType.CONNECTED
        spec.backoffPolicy shouldBe BackoffPolicy.EXPONENTIAL
        spec.backoffDelayDuration shouldBe 30 * 60 * 1000L
    }

    @Test fun `the periodic arm leaves the one-time lanes untouched`() = runTest2 {
        create().armPeriodicSweep()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test fun `a passed deadline schedules nothing`() = runTest2 {
        create().armForUnackedPurchases(expiresAt = System.currentTimeMillis() - 60 * 1000L)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }
}
