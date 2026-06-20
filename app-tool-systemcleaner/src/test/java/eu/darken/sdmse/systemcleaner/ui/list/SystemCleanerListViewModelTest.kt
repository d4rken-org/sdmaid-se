package eu.darken.sdmse.systemcleaner.ui.list

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
import java.time.Instant

class SystemCleanerListViewModelTest : BaseTest() {

    private class Harness(
        val vm: SystemCleanerListViewModel,
        val systemCleaner: SystemCleaner,
        val taskSubmitter: TaskSubmitter,
        val stateFlow: MutableStateFlow<SystemCleaner.State>,
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
        vm: SystemCleanerListViewModel,
    ): CollectedEvents<SystemCleanerListViewModel.Event> {
        val list = mutableListOf<SystemCleanerListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(
        vm: SystemCleanerListViewModel,
    ): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun scState(
        data: SystemCleaner.Data? = null,
        progress: Progress.Data? = null,
    ) = SystemCleaner.State(
        data = data,
        progress = progress,
        areSystemFilterAvailable = true,
    )

    private fun harness(
        filterContents: List<FilterContent>? = emptyList(),
    ): Harness {
        val data = filterContents?.let { SystemCleaner.Data(filterContents = it) }
        val stateFlow = MutableStateFlow(scState(data = data))
        val progressFlow = MutableStateFlow<Progress.Data?>(null)
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
        val vm = SystemCleanerListViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            systemCleaner = systemCleaner,
            taskSubmitter = taskSubmitter,
        )
        return Harness(vm, systemCleaner, taskSubmitter, stateFlow, progressFlow)
    }

    private fun previewMatch(path: String, size: Long = 1024L): SystemCleanerFilter.Match.Deletion =
        SystemCleanerFilter.Match.Deletion(
            lookup = LocalPathLookup(
                lookedUp = LocalPath.build("storage", "emulated", "0", path),
                fileType = FileType.FILE,
                size = size,
                modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
                target = null,
            ),
        )

    private fun fakeUndo(count: Int): SystemCleaner.ExclusionUndo =
        mockk<SystemCleaner.ExclusionUndo>().apply {
            every { exclusionIds } returns (0 until count).map { "ex-$it" }.toSet()
        }

    // ──────────────────────── exclude tests (existing) ────────────────────────

    @Test
    fun `onExcludeSelected aggregates count across multiple live filters`() = runTest2 {
        val filterA = previewFilterContent(
            identifier = "a",
            items = listOf(previewMatch("a1"), previewMatch("a2")),
        )
        val filterB = previewFilterContent(
            identifier = "b",
            items = listOf(previewMatch("b1")),
        )
        val h = harness(listOf(filterA, filterB))

        coEvery { h.systemCleaner.exclude(eq("a"), any()) } returns fakeUndo(2)
        coEvery { h.systemCleaner.exclude(eq("b"), any()) } returns fakeUndo(1)

        h.vm.onExcludeSelected(setOf("a", "b"))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 3
        coVerify(exactly = 1) { h.systemCleaner.exclude(eq("a"), any()) }
        coVerify(exactly = 1) { h.systemCleaner.exclude(eq("b"), any()) }
    }

    @Test
    fun `onExcludeSelected skips filters that are no longer in state`() = runTest2 {
        val filterA = previewFilterContent(identifier = "a", items = listOf(previewMatch("a1")))
        val h = harness(listOf(filterA))

        coEvery { h.systemCleaner.exclude(eq("a"), any()) } returns fakeUndo(1)

        h.vm.onExcludeSelected(setOf("a", "stale"))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 1
        coVerify(exactly = 1) { h.systemCleaner.exclude(eq("a"), any()) }
        coVerify(exactly = 0) { h.systemCleaner.exclude(eq("stale"), any()) }
    }

    @Test
    fun `onExcludeSelected skips filters with no paths`() = runTest2 {
        val emptyFilter = previewFilterContent(identifier = "empty", items = emptyList())
        val nonEmpty = previewFilterContent(identifier = "non-empty", items = listOf(previewMatch("p1")))
        val h = harness(listOf(emptyFilter, nonEmpty))

        coEvery { h.systemCleaner.exclude(eq("non-empty"), any()) } returns fakeUndo(1)

        h.vm.onExcludeSelected(setOf("empty", "non-empty"))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 1
        coVerify(exactly = 0) { h.systemCleaner.exclude(eq("empty"), any()) }
        coVerify(exactly = 1) { h.systemCleaner.exclude(eq("non-empty"), any()) }
    }

    @Test
    fun `onExcludeSelected does nothing when every selected filter is stale`() = runTest2 {
        val h = harness(listOf(previewFilterContent(identifier = "a", items = listOf(previewMatch("a1")))))
        h.vm.onExcludeSelected(setOf("stale-only"))
        advanceUntilIdle()
        coVerify(exactly = 0) { h.systemCleaner.exclude(any(), any()) }
    }

    @Test
    fun `onExcludeSelected does nothing for an empty selection`() = runTest2 {
        val h = harness(listOf(previewFilterContent(identifier = "a", items = listOf(previewMatch("a1")))))
        h.vm.onExcludeSelected(emptySet())
        advanceUntilIdle()
        coVerify(exactly = 0) { h.systemCleaner.exclude(any(), any()) }
    }

    // ──────────────────────── delete + state tests (new) ────────────────────────

    @Test
    fun `onDeleteSelected does nothing for empty selection`() = runTest2 {
        val h = harness(listOf(previewFilterContent(identifier = "a", items = listOf(previewMatch("a1")))))
        val collected = collectEvents(h.vm)

        h.vm.onDeleteSelected(emptySet())
        advanceUntilIdle()

        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onDeleteSelected emits ConfirmDeletion with the selected ids`() = runTest2 {
        val h = harness(listOf(previewFilterContent(identifier = "a", items = listOf(previewMatch("a1")))))

        h.vm.onDeleteSelected(setOf("a"))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf("a")
    }

    @Test
    fun `onRowClick emits ConfirmDeletion with the single row identifier`() = runTest2 {
        val fc = previewFilterContent(identifier = "row", items = listOf(previewMatch("r1")))
        val h = harness(listOf(fc))
        val row = SystemCleanerListViewModel.Row(content = fc)

        h.vm.onRowClick(row)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf("row")
    }

    @Test
    fun `onDeleteConfirmed submits ProcessingTask filtered to valid ids only`() = runTest2 {
        val fc = previewFilterContent(identifier = "valid", items = listOf(previewMatch("v1")))
        val h = harness(listOf(fc))
        coEvery {
            h.taskSubmitter.submit(any<SystemCleanerProcessingTask>())
        } returns SystemCleanerProcessingTask.Success(affectedSpace = 100L, affectedPaths = emptySet())

        h.vm.onDeleteConfirmed(setOf("valid", "stale"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(SystemCleanerProcessingTask(targetFilters = setOf("valid")))
        }
    }

    @Test
    fun `onDeleteConfirmed does not submit when no valid ids remain`() = runTest2 {
        val h = harness(listOf(previewFilterContent(identifier = "live", items = listOf(previewMatch("v1")))))

        h.vm.onDeleteConfirmed(setOf("stale-only"))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onDeleteConfirmed emits TaskResult event on Success`() = runTest2 {
        val fc = previewFilterContent(identifier = "tr", items = listOf(previewMatch("t1")))
        val h = harness(listOf(fc))
        val success = SystemCleanerProcessingTask.Success(affectedSpace = 250L, affectedPaths = emptySet())
        coEvery { h.taskSubmitter.submit(any<SystemCleanerProcessingTask>()) } returns success

        h.vm.onDeleteConfirmed(setOf("tr"))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.TaskResult>()
        event.result shouldBe success
    }

    @Test
    fun `state rows is null when Data is null`() = runTest2 {
        val h = harness(filterContents = null)
        h.vm.state.first().rows shouldBe null
    }

    @Test
    fun `state rows is empty when filterContents is empty`() = runTest2 {
        val h = harness(filterContents = emptyList())
        h.vm.state.first().rows shouldBe emptyList()
    }

    @Test
    fun `state rows are sorted by size descending`() = runTest2 {
        val small = previewFilterContent(identifier = "small", items = listOf(previewMatch("s1", size = 100L)))
        val large = previewFilterContent(identifier = "large", items = listOf(previewMatch("l1", size = 10_000L)))
        val medium = previewFilterContent(identifier = "medium", items = listOf(previewMatch("m1", size = 1_000L)))
        val h = harness(listOf(small, large, medium))

        val rows = h.vm.state.first().rows!!

        rows.map { it.identifier } shouldBe listOf("large", "medium", "small")
    }

    @Test
    fun `init navigates up when Data drains from non-empty to empty filterContents`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(previewMatch("a")))
        val h = harness(listOf(fc))

        // drop(1) skips replay; second emission with empty filterContents triggers navUp.
        h.stateFlow.value = scState(data = SystemCleaner.Data(filterContents = emptyList()))
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does NOT navigate up when Data transitions from non-empty to null (fresh re-scan)`() = runTest2 {
        // Fixed: SystemCleanerListViewModel now uses `mapNotNull { it.data }` (like
        // FilterContentDetailsViewModel), so the null transition a fresh scan publishes while
        // loading is skipped and no longer triggers navUp. (was BUG-FIXME-9)
        val fc = previewFilterContent(identifier = "f", items = listOf(previewMatch("a")))
        val h = harness(listOf(fc))
        val nav = collectNavEvents(h.vm)

        h.stateFlow.value = scState(data = null)
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }
}
