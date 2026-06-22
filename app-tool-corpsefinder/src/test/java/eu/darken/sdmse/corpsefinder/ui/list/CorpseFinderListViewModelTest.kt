package eu.darken.sdmse.corpsefinder.ui.list

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class CorpseFinderListViewModelTest : BaseTest() {

    private fun corpse(name: String, size: Long): Corpse = previewCorpse(
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", name),
            size = size,
        ),
        content = emptyList(),
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
        val vm: CorpseFinderListViewModel,
        val corpseFinder: CorpseFinder,
        val taskSubmitter: TaskSubmitter,
        val stateFlow: MutableStateFlow<CorpseFinder.State>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        val job: Job,
    ) {
        fun cancel() {
            job.cancel()
        }
    }

    private fun CoroutineScope.collectEvents(
        vm: CorpseFinderListViewModel,
    ): CollectedEvents<CorpseFinderListViewModel.Event> {
        val list = mutableListOf<CorpseFinderListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        data: CorpseFinder.Data? = null,
        progress: Progress.Data? = null,
    ): Harness {
        val stateFlow = MutableStateFlow(cfState(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
        val vm = CorpseFinderListViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            corpseFinder = corpseFinder,
            taskSubmitter = taskSubmitter,
        )
        return Harness(vm, corpseFinder, taskSubmitter, stateFlow, progressFlow)
    }

    @Test
    fun `state rows is null when Data is null`() = runTest2 {
        val h = harness(data = null)
        h.vm.state.first().rows shouldBe null
    }

    @Test
    fun `state rows is empty when Data corpses is empty`() = runTest2 {
        val h = harness(data = CorpseFinder.Data(corpses = emptyList()))
        h.vm.state.first().rows shouldBe emptyList()
    }

    @Test
    fun `state rows are sorted by size descending`() = runTest2 {
        val small = corpse(name = "small", size = 100)
        val large = corpse(name = "large", size = 10_000)
        val medium = corpse(name = "medium", size = 1_000)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(small, large, medium)))

        val rows = h.vm.state.first().rows!!
        rows.map { it.corpse.lookup.lookedUp } shouldBe listOf(
            large.lookup.lookedUp,
            medium.lookup.lookedUp,
            small.lookup.lookedUp,
        )
    }

    @Test
    fun `state progress passes through`() = runTest2 {
        val progress = Progress.Data()
        val h = harness(
            data = CorpseFinder.Data(corpses = emptyList()),
            progress = progress,
        )
        h.vm.state.first().progress shouldBe progress
    }

    @Test
    fun `progress-only emission preserves the rows instance (no re-sort)`() = runTest2 {
        // P0 perf guard: progress is decoupled from row production, so a progress tick must update
        // only the progress field and reuse the exact same rows List — the sort/map must NOT re-run.
        val h = harness(data = CorpseFinder.Data(corpses = listOf(corpse("a", 100), corpse("b", 200))))
        // Keep the WhileSubscribed state alive so the upstream chain actually runs.
        val job = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.state.collect { } }
        advanceUntilIdle()

        val rowsBefore = h.vm.state.value.rows
        rowsBefore.shouldNotBeNull()

        // Emit a progress-only change; data is untouched.
        val tick = Progress.Data(extra = "tick")
        h.progressFlow.value = tick
        advanceUntilIdle()

        h.vm.state.value.progress shouldBe tick
        // Same instance ⇒ the row pipeline did not re-execute on the progress tick.
        (h.vm.state.value.rows === rowsBefore) shouldBe true
        job.cancel()
    }

    @Test
    fun `onRowClick emits ConfirmDeletion with single id`() = runTest2 {
        val c = corpse("only", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))

        val row = h.vm.state.first().rows!!.single()
        h.vm.onRowClick(row)

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf(c.identifier)
    }

    @Test
    fun `onDeleteSelected with empty set does nothing`() = runTest2 {
        val h = harness(data = CorpseFinder.Data(corpses = listOf(corpse("a", 1))))
        val collected = collectEvents(h.vm)

        h.vm.onDeleteSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        // A regression that emitted ConfirmDeletion(emptySet()) would still pass the
        // no-submit check above. The event-collector catches that case too.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onDeleteSelected non-empty emits ConfirmDeletion`() = runTest2 {
        val c = corpse("a", 1)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))

        h.vm.onDeleteSelected(setOf(c.identifier))

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf(c.identifier)
    }

    @Test
    fun `onDeleteConfirmed submits task with valid ids only`() = runTest2 {
        val live = corpse("live", 100)
        val stale = corpse("stale", 200)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))

        val deleteSuccess = CorpseFinderDeleteTask.Success(
            affectedSpace = 100L,
            affectedPaths = setOf(live.lookup.lookedUp),
        )
        coEvery { h.taskSubmitter.submit(any()) } returns deleteSuccess

        h.vm.onDeleteConfirmed(setOf(live.identifier, stale.identifier))
        advanceUntilIdle()

        // The targeted call AND no other submit() calls — a regression that submitted a
        // valid task plus a stale one would still pass the specific match.
        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                CorpseFinderDeleteTask(targetCorpses = setOf(live.identifier)),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.TaskResult>()
        event.result shouldBe deleteSuccess
    }

    @Test
    fun `onDeleteConfirmed with all-stale ids does not submit`() = runTest2 {
        val live = corpse("live", 100)
        val stale1 = LocalPath.build("storage", "stale1")
        val stale2 = LocalPath.build("storage", "stale2")
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))

        h.vm.onDeleteConfirmed(setOf(stale1, stale2))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onExcludeSelected with empty set does nothing`() = runTest2 {
        val c = corpse("a", 1)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        val collected = collectEvents(h.vm)

        h.vm.onExcludeSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.corpseFinder.exclude(any()) }
        // Catches a regression that emitted ExclusionsCreated(count=0) with no exclude() call.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onExcludeSelected filters stale ids and calls corpseFinder exclude`() = runTest2 {
        val live = corpse("live", 100)
        val staleId = LocalPath.build("storage", "stale")
        val data = CorpseFinder.Data(corpses = listOf(live))
        val h = harness(data = data)

        coEvery { h.corpseFinder.exclude(any()) } returns CorpseFinder.ExclusionUndo(
            exclusionIds = setOf("exclusion-id-1"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onExcludeSelected(setOf(live.identifier, staleId))
        advanceUntilIdle()

        // Specific match + no extra exclude() calls.
        coVerify(exactly = 1) { h.corpseFinder.exclude(setOf(live.identifier)) }
        coVerify(exactly = 1) { h.corpseFinder.exclude(any()) }
    }

    @Test
    fun `onExcludeSelected event count reflects saved-exclusion count not validIds size`() = runTest2 {
        // Regression test for what used to be FIXME(corpsefinder-list-exclusion-count): the VM
        // now reads `undo.exclusionIds.size` from corpseFinder.exclude(), mirroring the details
        // VM. When ExclusionManager.save() coalesces duplicates (or otherwise saves fewer
        // exclusions than requested), the snackbar count must match what was actually saved.
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val data = CorpseFinder.Data(corpses = listOf(a, b))
        val h = harness(data = data)

        // Two ids selected, but only one exclusion actually saved (coalesced duplicate).
        coEvery { h.corpseFinder.exclude(any()) } returns CorpseFinder.ExclusionUndo(
            exclusionIds = setOf("only-one-saved"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onExcludeSelected(setOf(a.identifier, b.identifier))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.corpseFinder.exclude(setOf(a.identifier, b.identifier)) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.ExclusionsCreated>()
        // Fixed: emits undo.exclusionIds.size = 1, not validIds.size = 2.
        event.count shouldBe 1
    }

    @Test
    fun `init navigates up when Data drains from non-empty to empty`() = runTest2 {
        val c = corpse("a", 100)
        val initial = CorpseFinder.Data(corpses = listOf(c))
        val h = harness(data = initial)

        // drop(1) skips the initial replay; the second emission with empty data triggers navUp.
        h.stateFlow.value = cfState(data = CorpseFinder.Data(corpses = emptyList()))
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does not navigate up when Data transitions from non-empty to null`() = runTest2 {
        // Regression: data going to `null` indicates a fresh scan started (CorpseFinder sets
        // internalData = null at the top of performScan). That's a loading state, not an
        // "everything was excluded" state. navUp must NOT fire during loading.
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        val collected = collectNavEvents(h.vm)

        h.stateFlow.value = cfState(data = null)
        advanceUntilIdle()

        // No navUp emitted — the tightened filter (data != null && data.corpses.isEmpty())
        // rejects the null transition.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    private fun CoroutineScope.collectNavEvents(
        vm: CorpseFinderListViewModel,
    ): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }
}
