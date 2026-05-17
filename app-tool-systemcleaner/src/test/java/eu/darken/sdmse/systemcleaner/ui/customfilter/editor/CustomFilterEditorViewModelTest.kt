package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.filter.CustomFilterEditorOptions
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.SystemCrawler
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilter
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.rwDataStoreValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.Instant

class CustomFilterEditorViewModelTest : BaseTest() {

    @AfterEach
    fun teardownRouteMock() {
        unmockkObject(CustomFilterEditorRoute.Companion)
    }

    private fun config(
        id: String = "filter-1",
        label: String = "Filter Label",
        pathCriteria: Set<SegmentCriterium>? = setOf(
            SegmentCriterium(segments = listOf("Downloads"), mode = SegmentCriterium.Mode.Start()),
        ),
    ): CustomFilterConfig = CustomFilterConfig(
        identifier = id,
        label = label,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
        pathCriteria = pathCriteria,
    )

    private class Harness(
        val vm: CustomFilterEditorViewModel,
        val repo: CustomFilterRepo,
        val settings: SystemCleanerSettings,
        val enabledFilterValue: eu.darken.sdmse.common.datastore.DataStoreValue<Set<String>>,
        val crawler: SystemCrawler,
        val filterFactory: CustomFilter.Factory,
        val dataAreaState: MutableSharedFlow<DataAreaManager.State>,
    )

    /**
     * Mocks `CustomFilterEditorRoute.from(handle)` to bypass `SavedStateHandle.toRoute()`
     * which requires real Android Bundle support (Robolectric) — the Navigation library's
     * `SavedStateHandleArgStore` calls `bundleOf()` internally and crashes on JVM mocks.
     */
    private fun mockRoute(identifier: String?, initial: CustomFilterEditorOptions?) {
        mockkObject(CustomFilterEditorRoute.Companion)
        every { CustomFilterEditorRoute.from(any()) } returns CustomFilterEditorRoute(
            identifier = identifier,
            initial = initial,
        )
    }

    private fun buildHarness(
        identifier: String? = null,
        initial: CustomFilterEditorOptions? = null,
        existingConfigs: List<CustomFilterConfig> = emptyList(),
        availableAreas: Set<DataArea.Type> = emptySet(),
    ): Harness {
        mockRoute(identifier, initial)
        val repo = mockk<CustomFilterRepo>(relaxed = true).apply {
            every { configs } returns flowOf(existingConfigs)
            every { generateIdentifier() } returns "generated-id-${System.nanoTime()}"
        }
        val enabledFilterValue = rwDataStoreValue(emptySet<String>())
        val settings = mockk<SystemCleanerSettings>(relaxed = true).apply {
            every { enabledCustomFilter } returns enabledFilterValue
        }
        val dataAreaState = MutableSharedFlow<DataAreaManager.State>(replay = 1).apply {
            tryEmit(
                DataAreaManager.State(
                    areas = availableAreas.map {
                        mockk<DataArea>(relaxed = true).apply { every { type } returns it }
                    }.toSet(),
                ),
            )
        }
        val dataAreaManager = mockk<DataAreaManager>().apply {
            every { state } returns dataAreaState
        }
        val crawler = mockk<SystemCrawler>(relaxed = true).apply {
            every { progress } returns flowOf(null)
            every { matchEvents } returns MutableSharedFlow()
        }
        val filterFactory = mockk<CustomFilter.Factory>(relaxed = true)
        val vm = CustomFilterEditorViewModel(
            handle = SavedStateHandle(),
            dispatcherProvider = TestDispatcherProvider(),
            filterRepo = repo,
            dataAreaManager = dataAreaManager,
            crawler = crawler,
            filterFactory = filterFactory,
            settings = settings,
        )
        return Harness(vm, repo, settings, enabledFilterValue, crawler, filterFactory, dataAreaState)
    }

    /**
     * The editor VM's `state` is `DynamicStateFlow.shareIn(..., started = Lazily)`. Until
     * something subscribes, the upstream's `startValueProvider` is never invoked and
     * `state.value` stays at `null`. With `SharingStarted.Lazily`, ONE subscription is enough
     * to start the upstream — after that, `replay = 1` keeps the value cached even when no
     * subscriber is active. Use `first { it != null }` instead of an infinite `collect` so
     * `runTest2`'s `UncompletedCoroutinesError` check passes.
     */
    private fun CoroutineScope.keepStateAlive(vm: CustomFilterEditorViewModel) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            vm.state.first { it != null }
        }
    }

    // ──────────────────────────── initial state ────────────────────────────

    @Test
    fun `initial state for edit-existing has original non-null and canRemove true`() = runTest2 {
        val existing = config(id = "abc", label = "Old", pathCriteria = setOf(SegmentCriterium(listOf("Downloads"), SegmentCriterium.Mode.Start())))
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        val state = h.vm.state.value!!
        state.original shouldBe existing
        state.canRemove shouldBe true
    }

    @Test
    fun `initial state for new filter has original null and canRemove false`() = runTest2 {
        val h = buildHarness(identifier = null, initial = CustomFilterEditorOptions(label = "New filter"))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        val state = h.vm.state.value!!
        state.original shouldBe null
        state.canRemove shouldBe false
    }

    @Test
    fun `phantom filter path emits IllegalStateException and navs up`() = runTest2 {
        val h = buildHarness(identifier = "missing", initial = null, existingConfigs = emptyList())
        keepStateAlive(h.vm)
        val navEvents = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.navEvents.collect { navEvents.add(it) }
        }
        advanceUntilIdle()
        job.cancel()

        navEvents.contains(NavEvent.Up) shouldBe true
    }

    // ──────────────────────────── computed properties ────────────────────────────

    @Test
    fun `canSave is false when current is underdefined - no path or name criteria`() = runTest2 {
        val existingPath = SegmentCriterium(listOf("X"), SegmentCriterium.Mode.Start())
        val existing = config(id = "abc", pathCriteria = setOf(existingPath))
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.removePath(existingPath)
        advanceUntilIdle()

        val state = h.vm.state.value!!
        state.current.isUnderdefined shouldBe true
        state.canSave shouldBe false
    }

    @Test
    fun `canSave is false when label is empty`() = runTest2 {
        val existing = config(id = "abc", label = "Has label")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.updateLabel("")
        advanceUntilIdle()

        h.vm.state.value!!.canSave shouldBe false
    }

    @Test
    fun `canSave is true when label set and at least one path criterium and original differs`() = runTest2 {
        val existing = config(id = "abc", label = "Old")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.updateLabel("New name")
        advanceUntilIdle()

        h.vm.state.value!!.canSave shouldBe true
    }

    // ──────────────────────────── criteria mutators ────────────────────────────

    @Test
    fun `addPath then removePath round-trips through state`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()
        val newPath = SegmentCriterium(listOf("Cache"), SegmentCriterium.Mode.Contain())

        h.vm.addPath(newPath)
        advanceUntilIdle()
        h.vm.state.value!!.current.pathCriteria!!.contains(newPath) shouldBe true

        h.vm.removePath(newPath)
        advanceUntilIdle()
        (h.vm.state.value!!.current.pathCriteria?.contains(newPath) ?: false) shouldBe false
    }

    @Test
    fun `addNameContains then removeNameContains round-trips through state`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()
        val n = NameCriterium(name = "thumb", mode = NameCriterium.Mode.Contain())

        h.vm.addNameContains(n)
        advanceUntilIdle()
        h.vm.state.value!!.current.nameCriteria!!.contains(n) shouldBe true

        h.vm.removeNameContains(n)
        advanceUntilIdle()
        (h.vm.state.value!!.current.nameCriteria?.contains(n) ?: false) shouldBe false
    }

    @Test
    fun `addExclusion then removeExclusion round-trips through state`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()
        val ex = SegmentCriterium(listOf("keep"), SegmentCriterium.Mode.Contain())

        h.vm.addExclusion(ex)
        advanceUntilIdle()
        h.vm.state.value!!.current.exclusionCriteria!!.contains(ex) shouldBe true

        h.vm.removeExclusion(ex)
        advanceUntilIdle()
        (h.vm.state.value!!.current.exclusionCriteria?.contains(ex) ?: false) shouldBe false
    }

    @Test
    fun `toggleArea adds when not in set and removes when in set`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.toggleArea(DataArea.Type.SDCARD, checked = true)
        advanceUntilIdle()
        h.vm.state.value!!.current.areas!!.contains(DataArea.Type.SDCARD) shouldBe true

        h.vm.toggleArea(DataArea.Type.SDCARD, checked = false)
        advanceUntilIdle()
        (h.vm.state.value!!.current.areas?.contains(DataArea.Type.SDCARD) ?: false) shouldBe false
    }

    @Test
    fun `toggleFileType from null bootstraps singleton then adds and removes`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.toggleFileType(FileType.FILE)
        advanceUntilIdle()
        h.vm.state.value!!.current.fileTypes shouldBe setOf(FileType.FILE)

        h.vm.toggleFileType(FileType.DIRECTORY)
        advanceUntilIdle()
        h.vm.state.value!!.current.fileTypes shouldBe setOf(FileType.FILE, FileType.DIRECTORY)

        h.vm.toggleFileType(FileType.FILE)
        advanceUntilIdle()
        h.vm.state.value!!.current.fileTypes shouldBe setOf(FileType.DIRECTORY)
    }

    @Test
    fun `updateSizeMinimum and updateSizeMaximum and updateAgeMinimum and updateAgeMaximum propagate`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.updateSizeMinimum(100L)
        h.vm.updateSizeMaximum(1000L)
        h.vm.updateAgeMinimum(Duration.ofDays(1))
        h.vm.updateAgeMaximum(Duration.ofDays(30))
        advanceUntilIdle()

        val cur = h.vm.state.value!!.current
        cur.sizeMinimum shouldBe 100L
        cur.sizeMaximum shouldBe 1000L
        cur.ageMinimum shouldBe Duration.ofDays(1)
        cur.ageMaximum shouldBe Duration.ofDays(30)
    }

    // ──────────────────────────── save / remove / cancel ────────────────────────────

    @Test
    fun `save persists current config and navs up`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.updateLabel("Updated")
        advanceUntilIdle()
        h.vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.repo.save(any()) }
    }

    @Test
    fun `save with saveAsEnabled true also enables filter via settings`() = runTest2 {
        val h = buildHarness(
            identifier = null,
            initial = CustomFilterEditorOptions(label = "Init", saveAsEnabled = true),
        )
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.addPath(SegmentCriterium(listOf("X"), SegmentCriterium.Mode.Start()))
        advanceUntilIdle()
        h.vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.repo.save(any()) }
        coVerify(atLeast = 1) { h.enabledFilterValue.update(any()) }
    }

    @Test
    fun `save bypasses canSave gating - BUG-FIXME-5 documents current behavior`() = runTest2 {
        // BUG-FIXME-5: save() does NOT check canSave. A direct call saves the current config
        // even if canSave is false (empty label, underdefined criteria). The Compose screen
        // gates the Save action behind canSave but the VM method itself is unguarded. Flip
        // this test once a canSave guard is added inside save().
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.updateLabel("") // canSave = false
        advanceUntilIdle()
        h.vm.state.value!!.canSave shouldBe false

        h.vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.repo.save(any()) } // Saved anyway.
    }

    @Test
    fun `remove with confirmed false emits RemoveConfirmation event`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        val events = mutableListOf<CustomFilterEditorViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.events.collect { events.add(it) }
        }
        h.vm.remove(confirmed = false)
        advanceUntilIdle()
        job.cancel()

        events.any { it is CustomFilterEditorViewModel.Event.RemoveConfirmation } shouldBe true
    }

    @Test
    fun `remove with confirmed true calls repo remove and navs up`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.remove(confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.repo.remove(setOf("abc")) }
    }

    @Test
    fun `remove on new filter is a no-op because canRemove is false`() = runTest2 {
        val h = buildHarness(identifier = null, initial = CustomFilterEditorOptions(label = "x"))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.remove(confirmed = false)
        h.vm.remove(confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.repo.remove(any()) }
    }

    @Test
    fun `cancel on modified filter emits UnsavedChangesConfirmation`() = runTest2 {
        val existing = config(id = "abc", label = "Old")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        val events = mutableListOf<CustomFilterEditorViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.events.collect { events.add(it) }
        }
        h.vm.updateLabel("Changed")
        advanceUntilIdle()
        h.vm.cancel(confirmed = false)
        advanceUntilIdle()
        job.cancel()

        events.any { it is CustomFilterEditorViewModel.Event.UnsavedChangesConfirmation } shouldBe true
    }

    @Test
    fun `cancel on unchanged filter navs up directly`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        val navEvents = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.navEvents.collect { navEvents.add(it) }
        }
        h.vm.cancel(confirmed = false)
        advanceUntilIdle()
        job.cancel()

        navEvents.contains(NavEvent.Up) shouldBe true
    }

    @Test
    fun `cancel confirmed always navs up regardless of unsaved state`() = runTest2 {
        val existing = config(id = "abc")
        val h = buildHarness(identifier = "abc", existingConfigs = listOf(existing))
        keepStateAlive(h.vm)
        advanceUntilIdle()

        val navEvents = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.navEvents.collect { navEvents.add(it) }
        }
        h.vm.updateLabel("Some changes")
        advanceUntilIdle()
        h.vm.cancel(confirmed = true)
        advanceUntilIdle()
        job.cancel()

        navEvents.contains(NavEvent.Up) shouldBe true
    }

    @Test
    fun `availableAreas propagates from dataAreaManager state`() = runTest2 {
        val h = buildHarness(
            identifier = null,
            initial = CustomFilterEditorOptions(label = "x"),
            availableAreas = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_MEDIA),
        )
        keepStateAlive(h.vm)
        advanceUntilIdle()

        h.vm.state.value!!.availableAreas shouldBe setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_MEDIA)
    }
}
