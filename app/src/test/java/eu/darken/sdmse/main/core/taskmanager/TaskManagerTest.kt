package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.common.backup.BackupOperationGate
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.sharedresource.KeepAlive
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.main.core.SDMTool
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.selects.select
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class TaskManagerTest : BaseTest() {

    private val taskWorkerControl: TaskWorkerControl = mockk(relaxed = true)

    private val task: SDMTool.Task = mockk<SDMTool.Task>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
    }
    private val otherTask: SDMTool.Task = mockk<SDMTool.Task>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
    }
    private val taskResult: SDMTool.Task.Result = mockk<SDMTool.Task.Result>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
    }

    /**
     * The shared resources close their leases through `runBlocking`, which would deadlock against a
     * virtual-time-only scope, so every test scope is combined with [Dispatchers.IO].
     *
     * Passing [progress] makes the mock hold real progress state, so tests can observe whether a
     * cleanup path actually reset the tool's (tool-wide, not per-task) progress.
     */
    private fun createTool(
        scope: CoroutineScope,
        progress: AtomicReference<Progress.Data?>? = null,
    ): SDMTool = mockk<SDMTool>(relaxed = true).apply {
        every { type } returns SDMTool.Type.APPCLEANER
        every { sharedResource } returns SharedResource.createKeepAlive("test-tool", scope)
        coEvery { useRes<SDMTool.Task.Result>(any()) } coAnswers {
            firstArg<suspend (Any) -> SDMTool.Task.Result>().invoke(Unit)
        }
        if (progress != null) {
            every { updateProgress(any()) } answers {
                val update = firstArg<(Progress.Data?) -> Progress.Data?>()
                progress.set(update(progress.get()))
            }
        }
    }

    /**
     * Parks dispatched coroutine bodies per [Job] instead of running them. A LAZY task job whose
     * first dispatch is parked is guaranteed to be cancelled BEFORE its body - and with it the
     * `finally` that does the entry bookkeeping - ever ran. That is exactly the state the safety net
     * exists for; racing a live dispatcher would only hit that window by luck.
     */
    private class ParkingDispatcher : CoroutineDispatcher() {
        private val parked = mutableListOf<Pair<Job?, Runnable>>()
        private var parking = false

        fun park() {
            synchronized(parked) { parking = true }
        }

        fun release(job: Job) = drain { it === job }

        fun releaseAll() {
            synchronized(parked) { parking = false }
            drain { true }
        }

        private fun drain(matches: (Job?) -> Boolean) {
            val due = synchronized(parked) {
                val hits = parked.filter { matches(it.first) }
                parked.removeAll(hits)
                hits
            }
            due.forEach { (_, block) -> block.run() }
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(parked) {
                if (parking) {
                    parked.add(context[Job] to block)
                    return
                }
            }
            Dispatchers.IO.dispatch(context, block)
        }
    }

    /**
     * The entry map is private and the public `ManagedTask` projection deliberately carries neither
     * the job nor the resource lease. The safety-net tests need both: the job to cancel exactly this
     * entry (`cancel(type)` hits every task of the tool and does it from an async launch), and the
     * lease to assert it was actually released.
     */
    private fun TaskManager.rawEntries(): Collection<Any> {
        val field = TaskManager::class.java.getDeclaredField("taskEntries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entries = field.get(this) as MutableStateFlow<Map<String, Any>>
        return entries.value.values
    }

    private fun Any.entryField(name: String): Any? =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

    private fun TaskManager.jobOf(task: SDMTool.Task): Job =
        rawEntries().single { it.entryField("task") === task }.entryField("job") as Job

    /**
     * kotlinx' stacktrace recovery hands a caller that awaited across a suspension point a COPY of
     * the failure that chains the original, so identity is asserted along the cause chain.
     */
    private fun Throwable.carries(original: Throwable): Boolean =
        generateSequence(this) { it.cause }.any { it === original }

    private fun createManager(
        scope: CoroutineScope,
        tool: SDMTool,
        dispatcher: CoroutineDispatcher? = null,
    ) = TaskManager(
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(dispatcher),
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

    @Test fun `a task cancelled before its body ran is still stamped complete`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            val dispatcher = ParkingDispatcher()
            val manager = createManager(scope, tool, dispatcher)

            dispatcher.park()
            val caller = async(Dispatchers.IO) { runCatching { manager.submit(task) } }
            manager.state.first { it.tasks.isNotEmpty() }

            // Cancelled while the body sits parked: the finally that normally does the bookkeeping
            // never runs, so without the safety net completedAt would stay null forever.
            val job = manager.jobOf(task)
            job.cancel()
            dispatcher.releaseAll()

            manager.state.first { it.tasks.single().isComplete }.tasks.single().let {
                it.completedAt.shouldNotBeNull()
                it.isComplete shouldBe true
            }
            // ...and the submit() caller is released instead of waiting on a job that never
            // publishes anything.
            caller.await().isFailure shouldBe true
            // Guards the setup itself: if the body had run, the normal finally would have done the
            // bookkeeping and this test would pass without ever exercising the safety net.
            coVerify(exactly = 0) { tool.submit(any()) }
        }

    @Test fun `the safety net keeps the progress of another incomplete task for the same tool`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val progress = AtomicReference<Progress.Data?>(null)
            val tool = createTool(scope, progress)
            val dispatcher = ParkingDispatcher()
            val manager = createManager(scope, tool, dispatcher)

            dispatcher.park()
            val first = async(Dispatchers.IO) { runCatching { manager.submit(task) } }
            manager.state.first { it.tasks.size == 1 }
            val second = async(Dispatchers.IO) { runCatching { manager.submit(otherTask) } }
            manager.state.first { it.tasks.size == 2 }

            // Progress is tool-wide, not per-task. This stands in for what the still-incomplete
            // first task owns; clearing it would drop the dashboard's running state for live work.
            val owned = Progress.Data(extra = "owned-by-the-other-task")
            progress.set(owned)

            val secondJob = manager.jobOf(otherTask)
            secondJob.cancel()
            dispatcher.release(secondJob)

            manager.state.first { st -> st.tasks.single { it.task === otherTask }.isComplete }
            progress.get() shouldBe owned
            coVerify(exactly = 0) { tool.submit(any()) }

            val firstJob = manager.jobOf(task)
            firstJob.cancel()
            dispatcher.releaseAll()
            manager.state.first { st -> st.tasks.all { it.isComplete } }
            first.await()
            second.await()
        }

    @Test fun `the safety net clears tool progress when nothing else is running for that tool`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val progress = AtomicReference<Progress.Data?>(null)
            val tool = createTool(scope, progress)
            val dispatcher = ParkingDispatcher()
            val manager = createManager(scope, tool, dispatcher)

            dispatcher.park()
            val caller = async(Dispatchers.IO) { runCatching { manager.submit(task) } }
            manager.state.first { it.tasks.isNotEmpty() }
            progress.set(Progress.Data(extra = "stale"))

            val job = manager.jobOf(task)
            job.cancel()
            dispatcher.releaseAll()

            manager.state.first { it.tasks.single().isComplete }
            progress.get() shouldBe null
            coVerify(exactly = 0) { tool.submit(any()) }
            caller.await()
        }

    @Test fun `the safety net releases the resource lock even when the progress reset throws`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            val dispatcher = ParkingDispatcher()
            val manager = createManager(scope, tool, dispatcher)

            dispatcher.park()
            val caller = async(Dispatchers.IO) { runCatching { manager.submit(task) } }
            manager.state.first { it.tasks.isNotEmpty() }
            every { tool.updateProgress(any()) } throws IllegalStateException("Progress is broken")

            val job = manager.jobOf(task)
            job.cancel()
            dispatcher.releaseAll()

            manager.state.first { it.tasks.single().isComplete }
            // A throwing progress reset must not skip the two steps after it: leaking the lease
            // would keep the TaskManager's shared resources alive for the rest of the process.
            val entry = manager.rawEntries().single()
            (entry.entryField("resourceLock") as KeepAlive).isClosed shouldBe true
            coVerify(exactly = 0) { tool.submit(any()) }
            caller.await()
        }

    @Test fun `submitIfToolIdle runs the task when the tool has nothing in flight`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            coEvery { tool.submit(task) } returns taskResult

            val manager = createManager(scope, tool)

            manager.submitIfToolIdle(task) shouldBeSameInstanceAs taskResult
        }

    @Test fun `submitIfToolIdle declines while the tool has an incomplete task`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            val dispatcher = ParkingDispatcher()
            val manager = createManager(scope, tool, dispatcher)

            dispatcher.park()
            val caller = async(Dispatchers.IO) { runCatching { manager.submit(task) } }
            manager.state.first { it.tasks.isNotEmpty() }

            manager.submitIfToolIdle(otherTask) shouldBe null
            manager.state.first().tasks.size shouldBe 1

            val job = manager.jobOf(task)
            job.cancel()
            dispatcher.releaseAll()
            caller.await()
        }

    @Test fun `two concurrent submitIfToolIdle calls register exactly one task`() =
        runTest2(autoCancel = true) {
            val scope = this + Dispatchers.IO
            val tool = createTool(scope)
            val dispatcher = ParkingDispatcher()
            val manager = createManager(scope, tool, dispatcher)

            // The winner's body stays parked, so it is incomplete for the whole race: whichever
            // caller takes managerLock second MUST see it and decline. A check that ran outside the
            // registration's lock could let both through.
            dispatcher.park()
            val racers = listOf(task, otherTask).map { candidate ->
                async(Dispatchers.IO) { runCatching { manager.submitIfToolIdle(candidate) } }
            }

            // The decliner returns immediately; the winner is stuck on its parked job.
            val declined = select { racers.forEach { racer -> racer.onAwait { it } } }
            declined.isSuccess shouldBe true
            declined.getOrNull() shouldBe null
            manager.state.first().tasks.size shouldBe 1

            val winner = manager.rawEntries().single().entryField("job") as Job
            winner.cancel()
            dispatcher.releaseAll()
            racers.forEach { it.await() }
            manager.state.first().tasks.size shouldBe 1
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
