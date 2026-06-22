package eu.darken.sdmse.corpsefinder.ui.details

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class CorpseDetailsViewModelTest : BaseTest() {

    private fun corpse(name: String, size: Long, contentSizes: List<Long> = emptyList()): Corpse = previewCorpse(
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", name),
            size = size,
        ),
        content = contentSizes.mapIndexed { idx, csize ->
            previewLocalPathLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", name, "file$idx"),
                size = csize,
            )
        },
    )

    private fun cfState(
        data: CorpseFinder.Data? = null,
        progress: Progress.Data? = null,
    ): CorpseFinder.State = CorpseFinder.State(
        data = data,
        progress = progress,
        isFilterPrivateDataAvailable = false,
        isFilterDalvikCacheAvailable = false,
        isFilterArtProfilesAvailable = false,
        isFilterAppLibrariesAvailable = false,
        isFilterAppSourcesAvailable = false,
        isFilterPrivateAppSourcesAvailable = false,
        isFilterEncryptedAppResourcesAvailable = false,
    )

    private class Harness(
        val vm: CorpseDetailsViewModel,
        val corpseFinder: CorpseFinder,
        val taskSubmitter: TaskSubmitter,
        val stateFlow: MutableStateFlow<CorpseFinder.State>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
    )

    private fun harness(
        data: CorpseFinder.Data? = null,
        progress: Progress.Data? = null,
        savedHandle: SavedStateHandle = SavedStateHandle(),
    ): Harness {
        val stateFlow = MutableStateFlow(cfState(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)
        val taskStateFlow = MutableStateFlow(TaskSubmitter.State(tasks = emptySet()))
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
            every { state } returns taskStateFlow
        }
        val vm = CorpseDetailsViewModel(
            handle = savedHandle,
            dispatcherProvider = TestDispatcherProvider(),
            corpseFinder = corpseFinder,
            taskSubmitter = taskSubmitter,
        )
        return Harness(vm, corpseFinder, taskSubmitter, stateFlow, taskStateFlow)
    }

    @Test
    fun `bindRoute is idempotent`() = runTest2 {
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        val first = CorpseDetailsRoute(corpsePath = c.identifier)
        val second = CorpseDetailsRoute(corpsePath = LocalPath.build("storage", "different"))

        h.vm.bindRoute(first)
        h.vm.bindRoute(second)

        // First bind wins; state target reflects it.
        h.vm.state.first().target shouldBe c.identifier
    }

    @Test
    fun `state items are sorted by size descending`() = runTest2 {
        val small = corpse("small", 100)
        val large = corpse("large", 10_000)
        val medium = corpse("medium", 1_000)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(small, large, medium)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = large.identifier))

        val items = h.vm.state.first().items
        items.map { it.lookup.lookedUp } shouldBe listOf(
            large.lookup.lookedUp,
            medium.lookup.lookedUp,
            small.lookup.lookedUp,
        )
    }

    @Test
    fun `state target initially matches route corpsePath`() = runTest2 {
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(a, b)))

        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = a.identifier))
        h.vm.state.first().target shouldBe a.identifier
    }

    @Test
    fun `state target switches after onPageChanged`() = runTest2 {
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(a, b)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = a.identifier))

        h.vm.onPageChanged(b.identifier)
        advanceUntilIdle()

        // Re-emit the same data to force a recombination of state with the new currentTarget.
        h.stateFlow.value = cfState(data = CorpseFinder.Data(corpses = listOf(a, b)))
        h.vm.state.first().target shouldBe b.identifier
    }

    @Test
    fun `state target falls back via lastPosition when requested target is deleted`() = runTest2 {
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val c = corpse("c", 50)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(a, b, c)))

        // Sorted desc by size: [b, a, c]. Bind to a — its index is 1 → lastPosition becomes 1.
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = a.identifier))
        h.vm.state.first().target shouldBe a.identifier

        // Remove `a`. Sorted desc: [b, c]. lastPosition=1 → falls back to index min(1, 1)=1 → c.
        h.stateFlow.value = cfState(data = CorpseFinder.Data(corpses = listOf(b, c)))
        h.vm.state.first().target shouldBe c.identifier
    }

    @Test
    fun `onConfirmDeleteCorpse with stale id does not submit`() = runTest2 {
        val live = corpse("live", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        h.vm.onConfirmDeleteCorpse(LocalPath.build("storage", "stale"))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onConfirmDeleteCorpse with valid id submits delete task`() = runTest2 {
        val live = corpse("live", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        h.vm.onConfirmDeleteCorpse(live.identifier)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(CorpseFinderDeleteTask(targetCorpses = setOf(live.identifier)))
        }
        // No extra submit() calls — catches regressions that submit an additional wrong task.
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onConfirmDeleteContent with empty paths does not submit`() = runTest2 {
        val live = corpse("live", 100, contentSizes = listOf(50, 50))
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        h.vm.onConfirmDeleteContent(live.identifier, emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onConfirmDeleteContent filters paths not in corpse content`() = runTest2 {
        val live = corpse("live", 100, contentSizes = listOf(50, 50))
        val ownedPath = live.content.first().lookedUp
        val unownedPath = LocalPath.build("storage", "unowned")
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        h.vm.onConfirmDeleteContent(live.identifier, setOf(ownedPath, unownedPath))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                CorpseFinderDeleteTask(
                    targetCorpses = setOf(live.identifier),
                    targetContent = setOf(ownedPath),
                ),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onConfirmDeleteContent with all-invalid paths does not submit`() = runTest2 {
        val live = corpse("live", 100, contentSizes = listOf(50))
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        h.vm.onConfirmDeleteContent(
            live.identifier,
            setOf(LocalPath.build("storage", "unowned1"), LocalPath.build("storage", "unowned2")),
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onExcludeCorpse with stale id does nothing`() = runTest2 {
        val live = corpse("live", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        h.vm.onExcludeCorpse(LocalPath.build("storage", "stale"))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.corpseFinder.exclude(any()) }
    }

    @Test
    fun `onExcludeCorpse with valid id calls exclude and emits ExclusionsCreated`() = runTest2 {
        val live = corpse("live", 100)
        val data = CorpseFinder.Data(corpses = listOf(live))
        val h = harness(data = data)
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = live.identifier))

        val undo = CorpseFinder.ExclusionUndo(
            exclusionIds = setOf("excl-1", "excl-2"),
            previousData = data,
            postExcludeData = data,
        )
        coEvery { h.corpseFinder.exclude(setOf(live.identifier)) } returns undo

        h.vm.onExcludeCorpse(live.identifier)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseDetailsViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 2
        event.undo shouldBe undo
        event.restoreTarget shouldBe live.identifier
    }

    @Test
    fun `onUndoExclude restores target and calls undoExclude`() = runTest2 {
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val data = CorpseFinder.Data(corpses = listOf(a, b))
        val h = harness(data = data)
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = a.identifier))

        val undo = CorpseFinder.ExclusionUndo(
            exclusionIds = setOf("excl-1"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onUndoExclude(undo, restoreTarget = b.identifier)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.corpseFinder.undoExclude(undo) }
        h.vm.state.first().target shouldBe b.identifier
    }

    @Test
    fun `init navigates up when Data drains from non-empty to empty`() = runTest2 {
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))

        h.stateFlow.value = cfState(data = CorpseFinder.Data(corpses = emptyList()))
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does not navigate up when Data transitions from non-empty to null`() = runTest2 {
        // Regression: data going to `null` indicates a fresh scan started (CorpseFinder sets
        // internalData = null at the top of performScan). That's a loading state, not an
        // "everything was excluded" state — must not navigate the user away mid-scan.
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        val navCollected = mutableListOf<NavEvent>()
        val navJob = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.navEvents.collect { navCollected.add(it) }
        }

        h.stateFlow.value = cfState(data = null)
        advanceUntilIdle()

        navCollected shouldBe emptyList()
        navJob.cancel()
    }

    @Test
    fun `init filters out tasks that completed before VM init`() = runTest2 {
        // The init flow tracks `start = Instant.now()` at VM construction and only forwards
        // task results whose `completedAt > start`. A task that completed *before* the VM was
        // created (e.g. already-running tool result) must NOT emit an event.
        val c = corpse("a", 100)
        val staleResult = CorpseFinderDeleteTask.Success(
            affectedSpace = 0L,
            affectedPaths = emptySet(),
        )
        val staleTask = TaskSubmitter.ManagedTask(
            id = "stale",
            toolType = SDMTool.Type.CORPSEFINDER,
            task = CorpseFinderDeleteTask(),
            completedAt = Instant.now().minusSeconds(60),
            result = staleResult,
        )
        val h = harness(
            data = CorpseFinder.Data(corpses = listOf(c)),
        )
        // Set the stale task in the flow BEFORE we bind the route. drop nothing — make
        // the very first state emission contain the already-completed task.
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(staleTask))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = c.identifier))
        advanceUntilIdle()

        // Now post a fresh task with completedAt AFTER VM init. Only this one should fire.
        val freshResult = CorpseFinderDeleteTask.Success(
            affectedSpace = 100L,
            affectedPaths = setOf(c.lookup.lookedUp),
        )
        val freshTask = TaskSubmitter.ManagedTask(
            id = "fresh",
            toolType = SDMTool.Type.CORPSEFINDER,
            task = CorpseFinderDeleteTask(targetCorpses = setOf(c.identifier)),
            completedAt = Instant.now().plusSeconds(60),
            result = freshResult,
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(staleTask, freshTask))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseDetailsViewModel.Event.TaskResult>()
        // Stale was filtered; the next event is the fresh result, not the stale one.
        event.result shouldBe freshResult
    }

    @Test
    fun `init emits TaskResult event for tasks completed after VM init`() = runTest2 {
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = c.identifier))

        val result = CorpseFinderDeleteTask.Success(
            affectedSpace = 100L,
            affectedPaths = setOf(c.lookup.lookedUp),
        )
        val completedTask = TaskSubmitter.ManagedTask(
            id = "task-1",
            toolType = SDMTool.Type.CORPSEFINDER,
            task = CorpseFinderDeleteTask(targetCorpses = setOf(c.identifier)),
            completedAt = Instant.now().plusSeconds(1),
            result = result,
        )

        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(completedTask))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseDetailsViewModel.Event.TaskResult>()
        event.result shouldBe result
    }

    @Test
    fun `init does not re-emit TaskResult for the same task id`() = runTest2 {
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        h.vm.bindRoute(CorpseDetailsRoute(corpsePath = c.identifier))

        val result = CorpseFinderDeleteTask.Success(
            affectedSpace = 100L,
            affectedPaths = setOf(c.lookup.lookedUp),
        )
        val task = TaskSubmitter.ManagedTask(
            id = "task-1",
            toolType = SDMTool.Type.CORPSEFINDER,
            task = CorpseFinderDeleteTask(targetCorpses = setOf(c.identifier)),
            completedAt = Instant.now().plusSeconds(1),
            result = result,
        )

        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(task))
        advanceUntilIdle()
        // Consume the first event.
        h.vm.events.first() shouldBe CorpseDetailsViewModel.Event.TaskResult(result)

        // Re-emit the SAME ManagedTask (same id). handledResults dedup must prevent another event.
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(task.copy(notifyOnFinish = false)))
        advanceUntilIdle()

        // No further event — events flow is exhausted. We just verify by submitting a different
        // task: the next emission should be the new task's result, not the old one.
        val result2 = CorpseFinderDeleteTask.Success(
            affectedSpace = 0L,
            affectedPaths = emptySet(),
        )
        val task2 = TaskSubmitter.ManagedTask(
            id = "task-2",
            toolType = SDMTool.Type.CORPSEFINDER,
            task = CorpseFinderDeleteTask(),
            completedAt = Instant.now().plusSeconds(2),
            result = result2,
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(task, task2))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseDetailsViewModel.Event.TaskResult>()
        event.result shouldBe result2
    }
}
