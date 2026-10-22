package eu.darken.sdmse.main.core.taskmanager

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import eu.darken.sdmse.common.BuildConfigWrap
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import javax.inject.Provider

class TaskWorkerControlTest : BaseTest() {

    private val enqueueFuture = mockk<ListenableFuture<Operation.State.SUCCESS>>().apply {
        every { get() } returns mockk()
    }
    private val operation = mockk<Operation>().apply {
        every { result } returns enqueueFuture
    }
    private val workManager = mockk<WorkManager>().apply {
        every {
            enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } returns operation
    }
    private val workManagerProvider = mockk<Provider<WorkManager>>().apply {
        every { get() } returns workManager
    }

    private fun create() = TaskWorkerControl(
        workerManager = workManagerProvider,
        dispatcherProvider = TestDispatcherProvider(),
    )

    @Test fun `constructing does not resolve WorkManager`() {
        create()

        verify(exactly = 0) { workManagerProvider.get() }
    }

    @Test fun `startMonitor enqueues the expedited task worker`() = runTest2 {
        create().startMonitor()

        verify(exactly = 1) { workManagerProvider.get() }
        val request = slot<OneTimeWorkRequest>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "${BuildConfigWrap.APPLICATION_ID}.taskmanager.worker",
                ExistingWorkPolicy.KEEP,
                capture(request),
            )
        }
        request.captured.workSpec.expedited shouldBe true
        request.captured.workSpec.outOfQuotaPolicy shouldBe OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
        verify(exactly = 1) { enqueueFuture.get() }
    }
}
