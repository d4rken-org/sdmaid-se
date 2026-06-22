package eu.darken.sdmse.squeezer.ui.list

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class SqueezerListViewModelTest : BaseTest() {

    private fun image(name: String, size: Long = 1024L): CompressibleImage = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("/storage/$name")),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
    )

    private fun squeezerState(
        data: Squeezer.Data? = null,
        progress: Progress.Data? = null,
    ): Squeezer.State = Squeezer.State(
        data = data,
        progress = progress,
    )

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
    }

    // Mirrors the helper used in CorpseFinderSettingsViewModelTest. `.value(value: T)` is an
    // extension that delegates to DataStoreValue.update — stub update to a no-op so MockK can
    // both succeed the call and verify it later.
    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private class Harness(
        val vm: SqueezerListViewModel,
        val squeezer: Squeezer,
        val settings: SqueezerSettings,
        val taskSubmitter: TaskSubmitter,
        val upgradeRepo: UpgradeRepo,
        val stateFlow: MutableStateFlow<Squeezer.State>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
        val layoutMode: DataStoreValue<LayoutMode>,
        val compressionQuality: DataStoreValue<Int>,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        val job: Job,
    ) {
        fun cancel() {
            job.cancel()
        }
    }

    private fun CoroutineScope.collectEvents(vm: SqueezerListViewModel): CollectedEvents<SqueezerListViewModel.Event> {
        val list = mutableListOf<SqueezerListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(vm: SqueezerListViewModel): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        data: Squeezer.Data? = null,
        progress: Progress.Data? = null,
        layoutMode: LayoutMode = LayoutMode.LINEAR,
        compressionQuality: Int = SqueezerSettings.DEFAULT_QUALITY,
        isPro: Boolean = true,
    ): Harness {
        val stateFlow = MutableStateFlow(squeezerState(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)

        val squeezer = mockk<Squeezer>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }

        val layoutValue = rwDataStoreValue(layoutMode)
        val qualityValue = rwDataStoreValue(compressionQuality)
        val settings = mockk<SqueezerSettings>().apply {
            every { this@apply.layoutMode } returns layoutValue
            every { this@apply.compressionQuality } returns qualityValue
        }

        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
        }

        val vm = SqueezerListViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            squeezer = squeezer,
            settings = settings,
            taskSubmitter = taskSubmitter,
            upgradeRepo = upgradeRepo,
        )
        return Harness(
            vm = vm,
            squeezer = squeezer,
            settings = settings,
            taskSubmitter = taskSubmitter,
            upgradeRepo = upgradeRepo,
            stateFlow = stateFlow,
            progressFlow = progressFlow,
            layoutMode = layoutValue,
            compressionQuality = qualityValue,
        )
    }

    // ─────────────────────────── state ───────────────────────────

    @Test
    fun `state media is null when Data is null`() = runTest2 {
        val h = harness(data = null)
        h.vm.state.first().media shouldBe null
    }

    @Test
    fun `state media is empty list when Data media is empty`() = runTest2 {
        val h = harness(data = Squeezer.Data(media = emptySet()))
        h.vm.state.first().media shouldBe emptyList()
    }

    @Test
    fun `state media is sorted by size descending`() = runTest2 {
        val small = image("small", size = 100)
        val large = image("large", size = 10_000)
        val medium = image("medium", size = 1_000)
        val h = harness(data = Squeezer.Data(media = setOf(small, large, medium)))

        h.vm.state.first().media!!.map { it.identifier } shouldBe listOf(
            large.identifier,
            medium.identifier,
            small.identifier,
        )
    }

    @Test
    fun `state passes through progress, layoutMode, and quality`() = runTest2 {
        val progress = Progress.Data()
        val h = harness(
            data = Squeezer.Data(media = emptySet()),
            progress = progress,
            layoutMode = LayoutMode.GRID,
            compressionQuality = 55,
        )

        val state = h.vm.state.first()
        state.progress shouldBe progress
        state.layoutMode shouldBe LayoutMode.GRID
        state.quality shouldBe 55
    }

    // ─────────────────────────── compress confirmation flow ───────────────────────────

    @Test
    fun `compress without confirmation emits ConfirmCompression and does not submit`() = runTest2 {
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))
        h.vm.state.first()  // prime state.value so compress() can read current media

        h.vm.compress(setOf(a.identifier))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerListViewModel.Event.ConfirmCompression>()
        event.items.map { it.identifier } shouldBe listOf(a.identifier)
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `compress with empty ids set does nothing`() = runTest2 {
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))
        h.vm.state.first()  // prime
        val collected = collectEvents(h.vm)

        h.vm.compress(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        // No event either — defends against a regression that emitted ConfirmCompression(items=[]).
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `compress with all-stale ids does nothing`() = runTest2 {
        val staleId = CompressibleMedia.Id(LocalPath.build("/stale").path)
        val h = harness(data = Squeezer.Data(media = setOf(image("real.jpg"))))
        h.vm.state.first()  // prime
        val collected = collectEvents(h.vm)

        h.vm.compress(setOf(staleId))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        // After items.isEmpty() check the function returns — no event emitted.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `compress confirmed and pro submits ProcessTask with Selected mode`() = runTest2 {
        val a = image("a.jpg")
        val b = image("b.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a, b)), isPro = true)
        h.vm.state.first()  // prime

        val processSuccess = SqueezerProcessTask.Success(
            affectedSpace = 100L,
            affectedPaths = setOf(a.path),
            processedCount = 1,
        )
        coEvery { h.taskSubmitter.submit(any()) } returns processSuccess

        h.vm.compress(setOf(a.identifier), confirmed = true, qualityOverride = 60)
        advanceUntilIdle()

        // Argument-matching is awkward when the task is parcelable + data-class equality; assert
        // both the specific call AND no other submit().
        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                SqueezerProcessTask(
                    mode = SqueezerProcessTask.TargetMode.Selected(setOf(a.identifier)),
                    qualityOverride = 60,
                ),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerListViewModel.Event.TaskResult>()
        event.result shouldBe processSuccess
    }

    @Test
    fun `compress confirmed when not pro navigates to UpgradeRoute and does not submit`() = runTest2 {
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)), isPro = false)
        h.vm.state.first()  // prime
        val collectedNav = collectNavEvents(h.vm)

        h.vm.compress(setOf(a.identifier), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        // Exactly one nav event, targeting the upgrade route.
        collectedNav.list.size shouldBe 1
        collectedNav.list.single().shouldBeInstanceOf<NavEvent.GoTo>()
        collectedNav.cancel()
    }

    // ─────────────────────────── compressAll ───────────────────────────

    @Test
    fun `compressAll returns when media is null`() = runTest2 {
        val h = harness(data = null)
        h.vm.state.first()  // prime (still null)
        val collected = collectEvents(h.vm)

        h.vm.compressAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `compressAll returns when media is empty`() = runTest2 {
        val h = harness(data = Squeezer.Data(media = emptySet()))
        h.vm.state.first()  // prime
        val collected = collectEvents(h.vm)

        h.vm.compressAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `compressAll emits ConfirmCompression with all media identifiers`() = runTest2 {
        val a = image("a.jpg", size = 10)
        val b = image("b.jpg", size = 20)
        val h = harness(data = Squeezer.Data(media = setOf(a, b)))
        h.vm.state.first()  // prime

        h.vm.compressAll()
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerListViewModel.Event.ConfirmCompression>()
        event.items.map { it.identifier }.toSet() shouldBe setOf(a.identifier, b.identifier)
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }  // unconfirmed → no submit
    }

    // ─────────────────────────── exclude ───────────────────────────

    @Test
    fun `exclude calls Squeezer exclude with the provided ids`() = runTest2 {
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))
        coEvery { h.squeezer.exclude(any()) } returns setOf("exclusion-1")

        h.vm.exclude(setOf(a.identifier))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.squeezer.exclude(setOf(a.identifier)) }
    }

    @Test
    fun `exclude event count matches saved-exclusion count not requested-ids size`() = runTest2 {
        // Regression test for what used to be FIXME(squeezer-list-exclusion-count): the VM now
        // emits `savedIds.size` returned by Squeezer.exclude, not `ids.size`. When
        // ExclusionManager.save() coalesces duplicates, the snackbar count matches what was
        // actually persisted. Reverting Squeezer.exclude to Unit (or the VM to ids.size) would
        // flip this assertion.
        val a = image("a.jpg")
        val b = image("b.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a, b)))

        // Two ids selected, but Squeezer.exclude returns only one saved exclusion id.
        coEvery { h.squeezer.exclude(any()) } returns setOf<ExclusionId>("only-one")

        h.vm.exclude(setOf(a.identifier, b.identifier))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerListViewModel.Event.ExclusionsCreated>()
        // Fixed: emits savedIds.size = 1, not requested ids.size = 2.
        event.count shouldBe 1
    }

    @Test
    fun `exclude with empty saved set emits ExclusionsCreated zero`() = runTest2 {
        // The VM does NOT short-circuit on empty ids — `Squeezer.exclude` is still called (it
        // handles the empty case internally). Pin this so we don't add a premature guard that
        // diverges from the no-data scenario where save() also returns empty.
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))
        coEvery { h.squeezer.exclude(any()) } returns emptySet()

        h.vm.exclude(setOf(a.identifier))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 0
    }

    // ─────────────────────────── toggleLayoutMode ───────────────────────────

    @Test
    fun `toggleLayoutMode LINEAR writes GRID`() = runTest2 {
        val h = harness(layoutMode = LayoutMode.LINEAR)

        h.vm.toggleLayoutMode()
        advanceUntilIdle()

        val captured = slot<(LayoutMode) -> LayoutMode?>()
        coVerify(exactly = 1) { h.layoutMode.update(capture(captured)) }
        captured.captured(LayoutMode.LINEAR) shouldBe LayoutMode.GRID
    }

    @Test
    fun `toggleLayoutMode GRID writes LINEAR`() = runTest2 {
        val h = harness(layoutMode = LayoutMode.GRID)

        h.vm.toggleLayoutMode()
        advanceUntilIdle()

        val captured = slot<(LayoutMode) -> LayoutMode?>()
        coVerify(exactly = 1) { h.layoutMode.update(capture(captured)) }
        captured.captured(LayoutMode.GRID) shouldBe LayoutMode.LINEAR
    }

    // ─────────────────────────── navigation ───────────────────────────

    @Test
    fun `openPreview emits navigation to PreviewRoute`() = runTest2 {
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))
        val collectedNav = collectNavEvents(h.vm)

        h.vm.openPreview(a)
        advanceUntilIdle()

        collectedNav.list.size shouldBe 1
        collectedNav.list.single().shouldBeInstanceOf<NavEvent.GoTo>()
        collectedNav.cancel()
    }

    @Test
    fun `openExclusionsList emits navigation to ExclusionsListRoute`() = runTest2 {
        val h = harness()
        val collectedNav = collectNavEvents(h.vm)

        h.vm.openExclusionsList()
        advanceUntilIdle()

        collectedNav.list.size shouldBe 1
        collectedNav.list.single().shouldBeInstanceOf<NavEvent.GoTo>()
        collectedNav.cancel()
    }

    // ─────────────────────────── init data-drain auto-navUp ───────────────────────────

    @Test
    fun `init navigates up when Data transitions from non-empty to empty`() = runTest2 {
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))

        // drop(1) skips the initial replay; the second emission with empty data triggers navUp.
        h.stateFlow.value = squeezerState(data = Squeezer.Data(media = emptySet()))
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does not navigate up when Data transitions from non-empty to null - loading state`() = runTest2 {
        // Regression: data going to `null` indicates a fresh scan started (Squeezer sets
        // internalData = null at the top of performScan). That's a loading state, not an
        // "everything was excluded" state. navUp must NOT fire during loading — the VM's
        // `.filterNotNull()` step is what defends against this.
        val a = image("a.jpg")
        val h = harness(data = Squeezer.Data(media = setOf(a)))
        val collectedNav = collectNavEvents(h.vm)

        h.stateFlow.value = squeezerState(data = null)
        advanceUntilIdle()

        collectedNav.list shouldBe emptyList()
        collectedNav.cancel()
    }
}
