package eu.darken.sdmse.squeezer.ui.setup

import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration

class SqueezerSetupViewModelTest : BaseTest() {

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private class Values(
        val scanPaths: DataStoreValue<SqueezerSettings.ScanPaths>,
        val compressionQuality: DataStoreValue<Int>,
        val minAge: DataStoreValue<Duration>,
        val minSizeBytes: DataStoreValue<Long>,
    )

    private class Harness(
        val vm: SqueezerSetupViewModel,
        val settings: SqueezerSettings,
        val squeezer: Squeezer,
        val taskSubmitter: eu.darken.sdmse.main.core.taskmanager.TaskSubmitter,
        val pathMapper: PathMapper,
        val navCtrl: NavigationController,
        val squeezerState: MutableStateFlow<Squeezer.State>,
        val pickerResults: MutableStateFlow<PickerResult?>,
        val values: Values,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        val job: Job,
    ) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectEvents(vm: SqueezerSetupViewModel): CollectedEvents<SqueezerSetupViewModel.Event> {
        val list = mutableListOf<SqueezerSetupViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(vm: SqueezerSetupViewModel): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        scanPaths: Set<APath> = emptySet(),
        compressionQuality: Int = SqueezerSettings.DEFAULT_QUALITY,
        minAge: Duration = SqueezerSettings.MIN_AGE_DEFAULT,
        minSizeBytes: Long = SqueezerSettings.MIN_FILE_SIZE,
        squeezerData: Squeezer.Data? = null,
        squeezerProgress: Progress.Data? = null,
    ): Harness {
        val scanPathsValue = rwDataStoreValue(SqueezerSettings.ScanPaths(paths = scanPaths))
        val qualityValue = rwDataStoreValue(compressionQuality)
        val minAgeValue = rwDataStoreValue(minAge)
        val minSizeValue = rwDataStoreValue(minSizeBytes)

        val settings = mockk<SqueezerSettings>().apply {
            every { this@apply.scanPaths } returns scanPathsValue
            every { this@apply.compressionQuality } returns qualityValue
            every { this@apply.minAge } returns minAgeValue
            every { this@apply.minSizeBytes } returns minSizeValue
        }

        val squeezerStateFlow = MutableStateFlow(Squeezer.State(data = squeezerData, progress = squeezerProgress))
        val squeezerProgressFlow = MutableStateFlow(squeezerProgress)
        val squeezer = mockk<Squeezer>(relaxed = true).apply {
            every { state } returns squeezerStateFlow
            every { progress } returns squeezerProgressFlow
        }

        val taskSubmitter = mockk<eu.darken.sdmse.main.core.taskmanager.TaskSubmitter>(relaxed = true)
        val pathMapper = mockk<PathMapper>(relaxed = true)
        val localGateway = mockk<LocalGateway>(relaxed = true).apply {
            // Default: walk yields nothing — showExample with non-empty paths but no eligible
            // images surfaces NoExampleFound. Tests that need a sample image override this.
            coEvery { walk(any(), any(), any()) } returns emptyFlow()
        }
        val storageEnvironment = mockk<StorageEnvironment>(relaxed = true)
        val mimeTypeTool = mockk<MimeTypeTool>(relaxed = true)

        val pickerResults = MutableStateFlow<PickerResult?>(null)
        val navCtrl = mockk<NavigationController>(relaxed = true).apply {
            every { consumeResults<PickerResult>(any()) } returns pickerResults.let { stateFlow ->
                kotlinx.coroutines.flow.flow {
                    stateFlow.collect { value -> if (value != null) emit(value) }
                }
            }
        }

        val vm = SqueezerSetupViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
            squeezer = squeezer,
            taskSubmitter = taskSubmitter,
            compressionEstimator = CompressionEstimator(),
            localGateway = localGateway,
            pathMapper = pathMapper,
            storageEnvironment = storageEnvironment,
            mimeTypeTool = mimeTypeTool,
            navCtrl = navCtrl,
        )
        return Harness(
            vm = vm,
            settings = settings,
            squeezer = squeezer,
            taskSubmitter = taskSubmitter,
            pathMapper = pathMapper,
            navCtrl = navCtrl,
            squeezerState = squeezerStateFlow,
            pickerResults = pickerResults,
            values = Values(
                scanPaths = scanPathsValue,
                compressionQuality = qualityValue,
                minAge = minAgeValue,
                minSizeBytes = minSizeValue,
            ),
        )
    }

    // ─────────────────────────── state ───────────────────────────

    @Test
    fun `state with empty paths has canStartScan false`() = runTest2 {
        val h = harness(scanPaths = emptySet())

        val state = h.vm.state.first()
        state.scanPaths shouldBe emptyList()
        state.canStartScan shouldBe false
    }

    @Test
    fun `state with non-empty paths has canStartScan true and sorts the paths`() = runTest2 {
        val a = LocalPath.build("/aaa")
        val b = LocalPath.build("/bbb")
        val c = LocalPath.build("/ccc")
        val h = harness(scanPaths = setOf(c, a, b))

        val state = h.vm.state.first()
        state.scanPaths.map { it.path } shouldBe listOf(a.path, b.path, c.path)
        state.canStartScan shouldBe true
    }

    @Test
    fun `state quality and minAge pass through from settings`() = runTest2 {
        val h = harness(
            scanPaths = setOf(LocalPath.build("/dcim")),
            compressionQuality = 65,
            minAge = Duration.ofDays(30),
        )

        val state = h.vm.state.first()
        state.quality shouldBe 65
        state.minAge shouldBe Duration.ofDays(30)
    }

    @Test
    fun `state estimatedSavingsPercent derives from CompressionEstimator for JPEG quality`() = runTest2 {
        // CompressionEstimator JPEG @ q=65 → ratio 0.50 → 50% savings.
        val h = harness(scanPaths = setOf(LocalPath.build("/dcim")), compressionQuality = 65)

        h.vm.state.first().estimatedSavingsPercent shouldBe 50
    }

    @Test
    fun `state estimatedSavingsPercent is zero when quality is 100`() = runTest2 {
        // q=100 → ratio 1.0 → 0% savings. Pin the boundary so a regression that rounded the
        // wrong direction doesn't get past CI.
        val h = harness(scanPaths = setOf(LocalPath.build("/dcim")), compressionQuality = 100)

        h.vm.state.first().estimatedSavingsPercent shouldBe 0
    }

    // ─────────────────────────── updateQuality / updateMinAge ───────────────────────────

    @Test
    fun `updateQuality writes through to settings`() = runTest2 {
        val h = harness()

        h.vm.updateQuality(45)
        advanceUntilIdle()

        val captured = slot<(Int) -> Int?>()
        coVerify(exactly = 1) { h.values.compressionQuality.update(capture(captured)) }
        captured.captured(80) shouldBe 45
    }

    @Test
    fun `updateMinAge writes through to settings`() = runTest2 {
        val h = harness()

        h.vm.updateMinAge(Duration.ofDays(180))
        advanceUntilIdle()

        val captured = slot<(Duration) -> Duration?>()
        coVerify(exactly = 1) { h.values.minAge.update(capture(captured)) }
        captured.captured(Duration.ofDays(0)) shouldBe Duration.ofDays(180)
    }

    // ─────────────────────────── updatePaths normalization ───────────────────────────

    @Test
    fun `updatePaths accepts LocalPath inputs and writes them through`() = runTest2 {
        val a = LocalPath.build("/dcim")
        val b = LocalPath.build("/pictures")
        val h = harness()

        h.vm.updatePaths(setOf(a, b))
        advanceUntilIdle()

        val captured = slot<(SqueezerSettings.ScanPaths) -> SqueezerSettings.ScanPaths?>()
        coVerify(exactly = 1) { h.values.scanPaths.update(capture(captured)) }
        val result = captured.captured(SqueezerSettings.ScanPaths())
        result!!.paths shouldBe setOf(a, b)
    }

    @Test
    fun `updatePaths emits PathsDropped when SAF roots cannot be mapped to LocalPath`() = runTest2 {
        // Path-normalizer caveat: SAFPath roots on USB OTG / second SD card can't be remapped to
        // a LocalPath since the squeezer pipeline can't `java.io.File` them. The VM must surface
        // those as `PathsDropped` so the UI can explain why the scan returned nothing — without
        // this event the user has no signal at all.
        val safPath = mockk<SAFPath>()
        val h = harness()
        coEvery { h.pathMapper.toLocalPath(safPath) } returns null  // unmappable

        h.vm.updatePaths(setOf(safPath))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerSetupViewModel.Event.PathsDropped>()
        event.droppedPaths shouldBe listOf(safPath)
    }

    @Test
    fun `updatePaths does not emit PathsDropped when all SAF roots are mappable`() = runTest2 {
        // The complement of the test above. PathMapper returns a LocalPath for every input → no
        // dropped paths → no event. A regression that always emitted PathsDropped (e.g. building
        // the event before checking emptiness) would fail here.
        val safPath = mockk<SAFPath>()
        val mapped = LocalPath.build("/storage/emulated/0/DCIM")
        val h = harness()
        coEvery { h.pathMapper.toLocalPath(safPath) } returns mapped
        val collected = collectEvents(h.vm)

        h.vm.updatePaths(setOf(safPath))
        advanceUntilIdle()

        collected.list.none { it is SqueezerSetupViewModel.Event.PathsDropped } shouldBe true
        collected.cancel()
    }

    // ─────────────────────────── openPathPicker ───────────────────────────

    @Test
    fun `openPathPicker navigates to PickerRoute`() = runTest2 {
        val current = LocalPath.build("/already")
        val h = harness(scanPaths = setOf(current))
        val collectedNav = collectNavEvents(h.vm)

        h.vm.openPathPicker()
        advanceUntilIdle()

        collectedNav.list.size shouldBe 1
        collectedNav.list.single().shouldBeInstanceOf<NavEvent.GoTo>()
        collectedNav.cancel()
    }

    // ─────────────────────────── startScan ───────────────────────────

    @Test
    fun `startScan with results navigates to SqueezerListRoute`() = runTest2 {
        val h = harness(squeezerData = null)  // pre-scan
        val scanResult = mockk<SqueezerScanTask.Result>(relaxed = true)
        coEvery { h.taskSubmitter.submit(any()) } answers {
            // After submit completes, squeezer state reflects a populated Data.
            h.squeezerState.value = Squeezer.State(
                data = Squeezer.Data(media = setOf(mockk(relaxed = true))),
                progress = null,
            )
            scanResult
        }
        val collectedNav = collectNavEvents(h.vm)

        h.vm.startScan()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.submit(any<SqueezerScanTask>()) }
        collectedNav.list.size shouldBe 1
        collectedNav.list.single().shouldBeInstanceOf<NavEvent.GoTo>()
        collectedNav.cancel()
    }

    @Test
    fun `startScan with empty results emits NoResultsFound`() = runTest2 {
        val h = harness(squeezerData = null)
        val scanResult = mockk<SqueezerScanTask.Result>(relaxed = true)
        coEvery { h.taskSubmitter.submit(any()) } answers {
            // After submit, scan finishes with an empty Data — UI should NOT navigate forward.
            h.squeezerState.value = Squeezer.State(
                data = Squeezer.Data(media = emptySet()),
                progress = null,
            )
            scanResult
        }
        val collectedNav = collectNavEvents(h.vm)

        h.vm.startScan()
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerSetupViewModel.Event.NoResultsFound>()
        // No navigation away from the setup screen on empty results.
        collectedNav.list shouldBe emptyList()
        collectedNav.cancel()
    }

    // ─────────────────────────── showExample ───────────────────────────

    @Test
    fun `showExample with empty scanPaths emits NoExampleFound`() = runTest2 {
        // findSampleImage short-circuits when there are no search paths — neither localGateway
        // nor mimeTypeTool should be invoked.
        val h = harness(scanPaths = emptySet())

        h.vm.showExample()
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<SqueezerSetupViewModel.Event.NoExampleFound>()
    }

    // ─────────────────────────── init picker bridge ───────────────────────────

    @Test
    fun `init picker result triggers updatePaths`() = runTest2 {
        // Sanity-check the consumeResults() wiring in `init {}`: pushing a PickerResult onto the
        // navCtrl flow must end up writing through to settings.scanPaths.update.
        val picked = LocalPath.build("/dcim")
        val h = harness()

        h.pickerResults.value = PickerResult(selectedPaths = setOf(picked))
        advanceUntilIdle()

        val captured = slot<(SqueezerSettings.ScanPaths) -> SqueezerSettings.ScanPaths?>()
        coVerify(atLeast = 1) { h.values.scanPaths.update(capture(captured)) }
        captured.captured(SqueezerSettings.ScanPaths())!!.paths shouldBe setOf(picked)
    }
}
