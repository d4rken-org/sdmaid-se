package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.common.backup.BackupOperationGate
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.main.core.SDMTool
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.IOException

class TaskManagerTest : BaseTest() {

    private val taskWorkerControl: TaskWorkerControl = mockk(relaxed = true)

    private val task: SDMTool.Task = mockk<SDMTool.Task>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
    }
    private val taskResult: SDMTool.Task.Result = mockk<SDMTool.Task.Result>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
    }

    /**
     * The shared resources close their leases through `runBlocking`, which would deadlock against a
     * virtual-time-only scope, so every test scope is combined with [Dispatchers.IO].
     */
    private fun createTool(scope: CoroutineScope): SDMTool = mockk<SDMTool>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
        every { sharedResource } returns SharedResource.createKeepAlive("test-tool", scope)
        coEvery { useRes<SDMTool.Task.Result>(any()) } coAnswers {
            firstArg<suspend (Any) -> SDMTool.Task.Result>().invoke(Unit)
        }
    }

    /**
     * kotlinx' stacktrace recovery hands a caller that awaited across a suspension point a COPY of
     * the failure that chains the original, so identity is asserted along the cause chain.
     */
    private fun Throwable.carries(original: Throwable): Boolean =
        generateSequence(this) { it.cause }.any { it === original }

    private fun createManager(scope: CoroutineScope, tool: SDMTool) = TaskManager(
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(),
        tools = setOf(tool),
        taskWorkerControl = taskWorkerControl,
        backupGate = BackupOperationGate(),
    )

    @Test fun `a successful task returns its result`() = runTest2(autoCancel = true) {
        val scope = this + Dispatchers.IO
        val tool = createTool(scope)
        coEvery { tool.submit(task) } returns taskResult

        val manager = createManager(scope, tool)

        manager.submit(task) shouldBeSameInstanceAs taskResult
    }

    @Test fun `an exception from the tool is rethrown to the caller`() = runTest2(autoCancel = true) {
        val scope = this + Dispatchers.IO
        val tool = createTool(scope)
        val boom = IOException("Disk on fire")
        coEvery { tool.submit(task) } throws boom

        val manager = createManager(scope, tool)

        shouldThrow<IOException> { manager.submit(task) }.carries(boom) shouldBe true
    }

    @Test fun `an error from the tool arrives wrapped instead of killing the process`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            val boom = OutOfMemoryError("Out of everything")
            coEvery { tool.submit(task) } throws boom

            val manager = createManager(scope, tool)

            val thrown = shouldThrow<TaskFatalErrorException> { manager.submit(task) }
            thrown.cause.shouldBeInstanceOf<OutOfMemoryError>()
            thrown.carries(boom) shouldBe true
        }

    @Test fun `a failing progress reset during cleanup does not strand the caller`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            var failProgress = false
            every { tool.updateProgress(any()) } answers {
                if (failProgress) throw IllegalStateException("Progress is broken")
            }
            coEvery { tool.submit(task) } coAnswers {
                failProgress = true
                taskResult
            }

            val manager = createManager(scope, tool)

            manager.submit(task) shouldBeSameInstanceAs taskResult
            manager.state.first().tasks.single().let {
                it.isComplete shouldBe true
                it.result shouldBeSameInstanceAs taskResult
            }
        }

    @Test fun `an error from the progress reset during cleanup does not strand the caller`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            var failProgress = false
            every { tool.updateProgress(any()) } answers {
                if (failProgress) throw AssertionError("Progress is very broken")
            }
            coEvery { tool.submit(task) } coAnswers {
                failProgress = true
                taskResult
            }

            val manager = createManager(scope, tool)

            manager.submit(task) shouldBeSameInstanceAs taskResult
            manager.state.first().tasks.single().let {
                it.isComplete shouldBe true
                it.result shouldBeSameInstanceAs taskResult
            }
        }
}
