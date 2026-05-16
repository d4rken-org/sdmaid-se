package eu.darken.sdmse.systemcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.filterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.stock.ScreenshotsFilter
import eu.darken.sdmse.systemcleaner.core.filter.stock.TrashedFilter
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
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

class FilterContentDetailsViewModelTest : BaseTest() {

    private class Harness(
        val vm: FilterContentDetailsViewModel,
        val systemCleaner: SystemCleaner,
        val taskSubmitter: TaskSubmitter,
        val stateFlow: MutableStateFlow<SystemCleaner.State>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
    )

    private class CollectedEvents<T>(val list: MutableList<T>, val job: Job) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectEvents(
        vm: FilterContentDetailsViewModel,
    ): CollectedEvents<FilterContentDetailsViewModel.Event> {
        val list = mutableListOf<FilterContentDetailsViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(
        vm: FilterContentDetailsViewModel,
    ): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun scState(data: SystemCleaner.Data? = null, progress: Progress.Data? = null) =
        SystemCleaner.State(
            data = data,
            progress = progress,
            areSystemFilterAvailable = true,
        )

    private fun match(name: String, parent: String = "f", size: Long = 1024L): SystemCleanerFilter.Match.Deletion =
        SystemCleanerFilter.Match.Deletion(
            lookup = LocalPathLookup(
                lookedUp = LocalPath.build("storage", "emulated", "0", parent, name),
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

    private fun harness(
        filterContents: List<FilterContent> = emptyList(),
        savedHandle: SavedStateHandle = SavedStateHandle(),
    ): Harness {
        val data = SystemCleaner.Data(filterContents = filterContents)
        val stateFlow = MutableStateFlow(scState(data = data))
        val progressFlow = MutableStateFlow<Progress.Data?>(null)
        val taskStateFlow = MutableStateFlow(TaskSubmitter.State(tasks = emptySet()))
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
            every { this@apply.state } returns taskStateFlow
        }
        val vm = FilterContentDetailsViewModel(
            handle = savedHandle,
            dispatcherProvider = TestDispatcherProvider(),
            systemCleaner = systemCleaner,
            taskSubmitter = taskSubmitter,
        )
        return Harness(vm, systemCleaner, taskSubmitter, stateFlow, taskStateFlow)
    }

    // ──────────────────────────── route + state ────────────────────────────

    @Test
    fun `bindRoute sets initial target from route filterIdentifier`() = runTest2 {
        val fc = previewFilterContent(identifier = "f1", items = listOf(match("a")))
        val h = harness(listOf(fc))

        h.vm.bindRoute(FilterContentDetailsRoute(filterIdentifier = "f1"))

        h.vm.state.first().target shouldBe "f1"
    }

    @Test
    fun `bindRoute is idempotent`() = runTest2 {
        val a = previewFilterContent(identifier = "a", items = listOf(match("a1")))
        val b = previewFilterContent(identifier = "b", items = listOf(match("b1")))
        val h = harness(listOf(a, b))

        h.vm.bindRoute(FilterContentDetailsRoute(filterIdentifier = "a"))
        h.vm.bindRoute(FilterContentDetailsRoute(filterIdentifier = "b"))

        h.vm.state.first().target shouldBe "a"
    }

    @Test
    fun `state items are sorted by size descending`() = runTest2 {
        val small = previewFilterContent(identifier = "small", items = listOf(match("s", size = 100L)))
        val large = previewFilterContent(identifier = "large", items = listOf(match("l", size = 10_000L)))
        val medium = previewFilterContent(identifier = "medium", items = listOf(match("m", size = 1_000L)))
        val h = harness(listOf(small, large, medium))
        h.vm.bindRoute(FilterContentDetailsRoute(filterIdentifier = "large"))

        h.vm.state.first().items.map { it.identifier } shouldBe listOf("large", "medium", "small")
    }

    // ──────────────────────────── auto-navUp behaviour ────────────────────────────

    @Test
    fun `autoNavUpOnEmpty fires navUp when data drains to empty filterContents`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))
        h.vm.bindRoute(FilterContentDetailsRoute(filterIdentifier = "f"))

        h.stateFlow.value = scState(data = SystemCleaner.Data(filterContents = emptyList()))
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `autoNavUpOnEmpty does NOT fire when data transitions to null - mapNotNull fix`() = runTest2 {
        // Regression test: FilterContentDetailsViewModel uses
        // `systemCleaner.state.mapNotNull { it.data }` so a null emission during scan-restart
        // does NOT trigger navUp. Verifies the fix at FilterContentDetailsViewModel.kt:53.
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))
        h.vm.bindRoute(FilterContentDetailsRoute(filterIdentifier = "f"))
        val collected = collectNavEvents(h.vm)

        h.stateFlow.value = scState(data = null)
        advanceUntilIdle()

        collected.list shouldBe emptyList()
        collected.cancel()
    }

    // ──────────────────────────── delete actions ────────────────────────────

    @Test
    fun `onConfirmDeleteFilter stale id does not submit`() = runTest2 {
        val fc = previewFilterContent(identifier = "real", items = listOf(match("r")))
        val h = harness(listOf(fc))

        h.vm.onConfirmDeleteFilter("ghost")
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onConfirmDeleteFilter valid id submits ProcessingTask scoped to that filter`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))

        h.vm.onConfirmDeleteFilter("f")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(SystemCleanerProcessingTask(targetFilters = setOf("f")))
        }
    }

    @Test
    fun `onConfirmDeleteFiles empty paths does not submit`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))

        h.vm.onConfirmDeleteFiles("f", emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onConfirmDeleteFiles intersects paths with live content`() = runTest2 {
        val live = match("live")
        val stalePath = LocalPath.build("storage", "emulated", "0", "f", "stale")
        val fc = previewFilterContent(identifier = "f", items = listOf(live))
        val h = harness(listOf(fc))

        h.vm.onConfirmDeleteFiles("f", setOf(live.path, stalePath))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                SystemCleanerProcessingTask(
                    targetFilters = setOf("f"),
                    targetContent = setOf(live.path),
                ),
            )
        }
    }

    @Test
    fun `onConfirmDeleteFiles all-stale paths does not submit`() = runTest2 {
        val live = match("live")
        val fc = previewFilterContent(identifier = "f", items = listOf(live))
        val h = harness(listOf(fc))
        val stalePath = LocalPath.build("storage", "emulated", "0", "f", "stale")

        h.vm.onConfirmDeleteFiles("f", setOf(stalePath))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    // ──────────────────────────── exclude actions ────────────────────────────

    @Test
    fun `onExcludeFilter stale id does nothing`() = runTest2 {
        val fc = previewFilterContent(identifier = "real", items = listOf(match("r")))
        val h = harness(listOf(fc))

        h.vm.onExcludeFilter("ghost")
        advanceUntilIdle()

        coVerify(exactly = 0) { h.systemCleaner.exclude(any(), any()) }
    }

    @Test
    fun `onExcludeFilter empty items does nothing`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = emptyList())
        val h = harness(listOf(fc))

        h.vm.onExcludeFilter("f")
        advanceUntilIdle()

        coVerify(exactly = 0) { h.systemCleaner.exclude(any(), any()) }
    }

    @Test
    fun `onExcludeFilter emits ExclusionsCreated with count from undo not from input set size`() = runTest2 {
        // Mirrors the CorpseFinder fix: count comes from undo.exclusionIds.size, not the
        // count of input paths. ExclusionManager.save() may coalesce duplicates.
        val a = match("a")
        val b = match("b")
        val fc = previewFilterContent(identifier = "f", items = listOf(a, b))
        val h = harness(listOf(fc))
        coEvery { h.systemCleaner.exclude(eq("f"), any()) } returns fakeUndo(1)

        h.vm.onExcludeFilter("f")
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<FilterContentDetailsViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 1 // not 2 even though we exclude 2 paths
        event.restoreTarget shouldBe "f"
    }

    @Test
    fun `onExcludeFilter emits ExclusionsCreated with count 0 when save returns empty - details VM does not suppress`() = runTest2 {
        // BUG-FIXME-4: Details VM emits ExclusionsCreated even when undo.exclusionIds is
        // empty — meaning the snackbar shows "0 exclusions created". List VM has a guard
        // (`if (totalExclusions == 0) return@launch`); details VM does not. Asymmetric
        // behaviour. Flip this test if details VM is updated to match list VM.
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))
        coEvery { h.systemCleaner.exclude(eq("f"), any()) } returns fakeUndo(0)

        h.vm.onExcludeFilter("f")
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<FilterContentDetailsViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 0
    }

    @Test
    fun `onExcludeFiles empty paths does nothing`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))

        h.vm.onExcludeFiles("f", emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.systemCleaner.exclude(any(), any()) }
    }

    @Test
    fun `onExcludeFiles intersects with live paths and emits SelectionExclusionsCreated`() = runTest2 {
        val live = match("live")
        val stalePath = LocalPath.build("storage", "emulated", "0", "f", "stale")
        val fc = previewFilterContent(identifier = "f", items = listOf(live))
        val h = harness(listOf(fc))
        coEvery { h.systemCleaner.exclude(eq("f"), eq(setOf(live.path))) } returns fakeUndo(1)

        h.vm.onExcludeFiles("f", setOf(live.path, stalePath))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<FilterContentDetailsViewModel.Event.SelectionExclusionsCreated>()
        event.count shouldBe 1
        coVerify(exactly = 1) { h.systemCleaner.exclude(eq("f"), eq(setOf(live.path))) }
    }

    @Test
    fun `onUndoExclude calls systemCleaner undoExclude with the handle`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))
        val undo = fakeUndo(2)

        h.vm.onUndoExclude(undo, restoreTarget = "f")
        advanceUntilIdle()

        coVerify(exactly = 1) { h.systemCleaner.undoExclude(undo) }
    }

    // ──────────────────────────── preview navigation gating ────────────────────────────

    @Test
    fun `onPreviewFile navigates only for TrashedFilter and ScreenshotsFilter identifiers`() = runTest2 {
        val trashedId = TrashedFilter::class.filterIdentifier
        val screenshotsId = ScreenshotsFilter::class.filterIdentifier
        val otherId = "some.other.filter"
        val fc = previewFilterContent(identifier = otherId, items = listOf(match("a")))
        val h = harness(listOf(fc))
        val collected = collectNavEvents(h.vm)

        h.vm.onPreviewFile(otherId, match("ignored").path)
        advanceUntilIdle()
        // No nav event for non-preview filters.
        collected.list.any { it is NavEvent.GoTo && it.destination is PreviewRoute } shouldBe false

        h.vm.onPreviewFile(trashedId, match("trashed").path)
        h.vm.onPreviewFile(screenshotsId, match("screenshot").path)
        advanceUntilIdle()

        val previewNavs = collected.list.count { it is NavEvent.GoTo && it.destination is PreviewRoute }
        previewNavs shouldBe 2
        collected.cancel()
    }

    // ──────────────────────────── TaskResult emission ────────────────────────────

    @Test
    fun `TaskResult event fires when a SYSTEMCLEANER task completes after init`() = runTest2 {
        val fc = previewFilterContent(identifier = "f", items = listOf(match("a")))
        val h = harness(listOf(fc))
        val collected = collectEvents(h.vm)
        advanceUntilIdle()
        val success = SystemCleanerProcessingTask.Success(affectedSpace = 10L, affectedPaths = emptySet())
        val completed = TaskSubmitter.ManagedTask(
            id = "t1",
            toolType = SDMTool.Type.SYSTEMCLEANER,
            task = SystemCleanerProcessingTask(),
            completedAt = Instant.now().plusSeconds(1),
            result = success,
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(completed))
        advanceUntilIdle()

        val taskEvent = collected.list.filterIsInstance<FilterContentDetailsViewModel.Event.TaskResult>().single()
        taskEvent.result shouldBe success
        collected.cancel()
    }
}
