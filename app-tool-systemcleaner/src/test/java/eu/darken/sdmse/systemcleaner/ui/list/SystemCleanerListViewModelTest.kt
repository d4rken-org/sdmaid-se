package eu.darken.sdmse.systemcleaner.ui.list

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class SystemCleanerListViewModelTest : BaseTest() {

    private fun harness(
        filterContents: List<FilterContent>,
    ): Pair<SystemCleanerListViewModel, SystemCleaner> {
        val data = SystemCleaner.Data(filterContents = filterContents)
        val state = MutableStateFlow(
            SystemCleaner.State(
                data = data,
                progress = null,
                areSystemFilterAvailable = true,
            ),
        )
        val progress = MutableStateFlow<Progress.Data?>(null)
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { this@apply.state } returns state
            every { this@apply.progress } returns progress
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
        val vm = SystemCleanerListViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            systemCleaner = systemCleaner,
            taskSubmitter = taskSubmitter,
        )
        return vm to systemCleaner
    }

    private fun previewMatch(path: String): SystemCleanerFilter.Match.Deletion =
        SystemCleanerFilter.Match.Deletion(
            lookup = LocalPathLookup(
                lookedUp = LocalPath.build("storage", "emulated", "0", path),
                fileType = FileType.FILE,
                size = 1024L,
                modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
                target = null,
            ),
        )

    private fun fakeUndo(count: Int): SystemCleaner.ExclusionUndo =
        mockk<SystemCleaner.ExclusionUndo>().apply {
            every { exclusionIds } returns (0 until count).map { "ex-$it" }.toSet()
        }

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
        val (vm, systemCleaner) = harness(listOf(filterA, filterB))

        coEvery { systemCleaner.exclude(eq("a"), any()) } returns fakeUndo(2)
        coEvery { systemCleaner.exclude(eq("b"), any()) } returns fakeUndo(1)

        vm.onExcludeSelected(setOf("a", "b"))
        advanceUntilIdle()

        val event = vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 3
        coVerify(exactly = 1) { systemCleaner.exclude(eq("a"), any()) }
        coVerify(exactly = 1) { systemCleaner.exclude(eq("b"), any()) }
    }

    @Test
    fun `onExcludeSelected skips filters that are no longer in state`() = runTest2 {
        val filterA = previewFilterContent(
            identifier = "a",
            items = listOf(previewMatch("a1")),
        )
        val (vm, systemCleaner) = harness(listOf(filterA))

        coEvery { systemCleaner.exclude(eq("a"), any()) } returns fakeUndo(1)

        vm.onExcludeSelected(setOf("a", "stale"))
        advanceUntilIdle()

        val event = vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 1
        coVerify(exactly = 1) { systemCleaner.exclude(eq("a"), any()) }
        coVerify(exactly = 0) { systemCleaner.exclude(eq("stale"), any()) }
    }

    @Test
    fun `onExcludeSelected skips filters with no paths`() = runTest2 {
        val emptyFilter = previewFilterContent(
            identifier = "empty",
            items = emptyList(),
        )
        val nonEmpty = previewFilterContent(
            identifier = "non-empty",
            items = listOf(previewMatch("p1")),
        )
        val (vm, systemCleaner) = harness(listOf(emptyFilter, nonEmpty))

        coEvery { systemCleaner.exclude(eq("non-empty"), any()) } returns fakeUndo(1)

        vm.onExcludeSelected(setOf("empty", "non-empty"))
        advanceUntilIdle()

        val event = vm.events.first()
        event.shouldBeInstanceOf<SystemCleanerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 1
        coVerify(exactly = 0) { systemCleaner.exclude(eq("empty"), any()) }
        coVerify(exactly = 1) { systemCleaner.exclude(eq("non-empty"), any()) }
    }

    @Test
    fun `onExcludeSelected does nothing when every selected filter is stale`() = runTest2 {
        val filterA = previewFilterContent(
            identifier = "a",
            items = listOf(previewMatch("a1")),
        )
        val (vm, systemCleaner) = harness(listOf(filterA))

        vm.onExcludeSelected(setOf("stale-only"))
        advanceUntilIdle()

        coVerify(exactly = 0) { systemCleaner.exclude(any(), any()) }
    }

    @Test
    fun `onExcludeSelected does nothing for an empty selection`() = runTest2 {
        val filterA = previewFilterContent(
            identifier = "a",
            items = listOf(previewMatch("a1")),
        )
        val (vm, systemCleaner) = harness(listOf(filterA))

        vm.onExcludeSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { systemCleaner.exclude(any(), any()) }
    }
}
