package eu.darken.sdmse.deduplicator.ui.settings

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.ResultKey
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.picker.PickerResultKey
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.ui.ArbiterConfigRoute
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class DeduplicatorSettingsViewModelTest : BaseTest() {

    // The `.value(value: T)` extension delegates to `DataStoreValue.update`. Stub `update` to a
    // no-op success so MockK can both succeed the call and verify it later.
    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
    }

    private class Values(
        val allowDeleteAll: DataStoreValue<Boolean>,
        val skipUncommon: DataStoreValue<Boolean>,
        val minSizeBytes: DataStoreValue<Long>,
        val isSleuthChecksumEnabled: DataStoreValue<Boolean>,
        val isSleuthPHashEnabled: DataStoreValue<Boolean>,
        val isSleuthMediaEnabled: DataStoreValue<Boolean>,
        val scanPaths: DataStoreValue<DeduplicatorSettings.ScanPaths>,
    )

    private class Harness(
        val vm: DeduplicatorSettingsViewModel,
        val settings: DeduplicatorSettings,
        val navCtrl: NavigationController,
        val pickerResults: MutableStateFlow<PickerResult?>,
        val capturedConsumeKey: io.mockk.CapturingSlot<ResultKey<PickerResult>>,
        val values: Values,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        private val job: Job,
    ) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectNavEvents(vm: DeduplicatorSettingsViewModel): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        isPro: Boolean = false,
        allowDeleteAll: Boolean = false,
        skipUncommon: Boolean = true,
        minSizeBytes: Long = DeduplicatorSettings.MIN_FILE_SIZE,
        sleuthChecksum: Boolean = true,
        sleuthPHash: Boolean = false,
        sleuthMedia: Boolean = false,
        scanPaths: Set<APath> = emptySet(),
    ): Harness {
        val values = Values(
            allowDeleteAll = rwDataStoreValue(allowDeleteAll),
            skipUncommon = rwDataStoreValue(skipUncommon),
            minSizeBytes = rwDataStoreValue(minSizeBytes),
            isSleuthChecksumEnabled = rwDataStoreValue(sleuthChecksum),
            isSleuthPHashEnabled = rwDataStoreValue(sleuthPHash),
            isSleuthMediaEnabled = rwDataStoreValue(sleuthMedia),
            scanPaths = rwDataStoreValue(DeduplicatorSettings.ScanPaths(paths = scanPaths)),
        )
        val settings = mockk<DeduplicatorSettings>().apply {
            every { this@apply.allowDeleteAll } returns values.allowDeleteAll
            every { this@apply.skipUncommon } returns values.skipUncommon
            every { this@apply.minSizeBytes } returns values.minSizeBytes
            every { this@apply.isSleuthChecksumEnabled } returns values.isSleuthChecksumEnabled
            every { this@apply.isSleuthPHashEnabled } returns values.isSleuthPHashEnabled
            every { this@apply.isSleuthMediaEnabled } returns values.isSleuthMediaEnabled
            every { this@apply.scanPaths } returns values.scanPaths
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
        }
        val pickerResults = MutableStateFlow<PickerResult?>(null)
        val capturedKey = slot<ResultKey<PickerResult>>()
        val navCtrl = mockk<NavigationController>(relaxed = true).apply {
            every { consumeResults<PickerResult>(capture(capturedKey)) } returns flow {
                pickerResults.collect { value -> if (value != null) emit(value) }
            }
        }
        val vm = DeduplicatorSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            upgradeRepo = upgradeRepo,
            settings = settings,
            navCtrl = navCtrl,
        )
        return Harness(vm, settings, navCtrl, pickerResults, capturedKey, values)
    }

    @Test
    fun `state passes through DataStore values when seeded with the production defaults`() = runTest2 {
        // The harness defaults mirror the production defaults declared in DeduplicatorSettings.
        // This test verifies the VM's combine() correctly threads each value into State; it
        // does NOT validate the production defaults themselves (those would need a real
        // DataStore). If DeduplicatorSettings flips a default, update this harness alongside.
        val h = harness()

        val state = h.vm.state.first()
        state.isPro shouldBe false
        state.scanPaths shouldBe emptyList()
        state.allowDeleteAll shouldBe false
        state.minSizeBytes shouldBe DeduplicatorSettings.MIN_FILE_SIZE
        state.skipUncommon shouldBe true
        state.isSleuthChecksumEnabled shouldBe true
        state.isSleuthPHashEnabled shouldBe false
        state.isSleuthMediaEnabled shouldBe false
    }

    @Test
    fun `state reflects non-default DataStore values`() = runTest2 {
        val h = harness(
            isPro = true,
            allowDeleteAll = true,
            skipUncommon = false,
            minSizeBytes = 4 * 1024 * 1024L,
            sleuthChecksum = false,
            sleuthPHash = true,
            sleuthMedia = true,
        )

        val state = h.vm.state.first()
        state.isPro shouldBe true
        state.allowDeleteAll shouldBe true
        state.skipUncommon shouldBe false
        state.minSizeBytes shouldBe 4 * 1024 * 1024L
        state.isSleuthChecksumEnabled shouldBe false
        state.isSleuthPHashEnabled shouldBe true
        state.isSleuthMediaEnabled shouldBe true
    }

    @Test
    fun `state scanPaths are sorted by path string`() = runTest2 {
        val a = LocalPath.build("storage", "aaa")
        val b = LocalPath.build("storage", "bbb")
        val c = LocalPath.build("storage", "ccc")
        val h = harness(scanPaths = setOf(c, a, b))

        val state = h.vm.state.first()
        state.scanPaths.map { it.path } shouldBe listOf(a.path, b.path, c.path)
    }

    @Test
    fun `init subscribes to picker results with the production result key`() = runTest2 {
        // Regression: VM and PickerRoute share `SCAN_PATHS_REQUEST_KEY`. If the consumer's key
        // drifts apart from the producer's request key, picker results land in a different
        // channel and the VM silently never sees them. Pin the resolved channel name.
        val h = harness()
        // Force the VM's init to run consumeResults() at least once.
        h.vm.state.first()

        h.capturedConsumeKey.captured.name shouldBe "picker.result.scan.location.paths"
    }

    @Test
    fun `onSearchLocationsClick navigates to a PickerRoute with DIRS mode and the current paths`() = runTest2 {
        val seeded = LocalPath.build("storage", "seed")
        val h = harness(scanPaths = setOf(seeded))
        // Prime state so onSearchLocationsClick's `state.value.scanPaths` is populated.
        h.vm.state.first()
        val nav = collectNavEvents(h.vm)

        h.vm.onSearchLocationsClick()
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination.shouldBeInstanceOf<PickerRoute>()
        route.request.requestKey shouldBe "scan.location.paths"
        route.request.mode shouldBe PickerRequest.PickMode.DIRS
        route.request.selectedPaths shouldBe listOf(seeded)
        route.request.allowedAreas shouldBe setOf(
            DataArea.Type.PORTABLE,
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_MEDIA,
        )
        nav.cancel()
    }

    @Test
    fun `onArbiterConfigClick navigates to ArbiterConfigRoute`() = runTest2 {
        val h = harness()
        val nav = collectNavEvents(h.vm)

        h.vm.onArbiterConfigClick()
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe ArbiterConfigRoute
        nav.cancel()
    }

    @Test
    fun `setAllowDeleteAll writes through`() = runTest2 {
        val h = harness()

        h.vm.setAllowDeleteAll(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.allowDeleteAll.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setSkipUncommon writes through`() = runTest2 {
        val h = harness()

        h.vm.setSkipUncommon(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.skipUncommon.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setMinSizeBytes writes through`() = runTest2 {
        val h = harness()

        h.vm.setMinSizeBytes(2 * 1024 * 1024L)
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minSizeBytes.update(capture(captured)) }
        captured.captured(0L) shouldBe 2 * 1024 * 1024L
    }

    @Test
    fun `resetMinSizeBytes writes the default constant`() = runTest2 {
        val h = harness(minSizeBytes = 9999L)

        h.vm.resetMinSizeBytes()
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minSizeBytes.update(capture(captured)) }
        captured.captured(0L) shouldBe DeduplicatorSettings.MIN_FILE_SIZE
    }

    @Test
    fun `setSleuthChecksumEnabled writes through`() = runTest2 {
        val h = harness()

        h.vm.setSleuthChecksumEnabled(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.isSleuthChecksumEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setSleuthPHashEnabled writes through`() = runTest2 {
        val h = harness()

        h.vm.setSleuthPHashEnabled(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.isSleuthPHashEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setSleuthMediaEnabled writes through`() = runTest2 {
        val h = harness()

        h.vm.setSleuthMediaEnabled(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.isSleuthMediaEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `resetScanPaths writes an empty ScanPaths replacing whatever was there`() = runTest2 {
        val seeded = DeduplicatorSettings.ScanPaths(paths = setOf(LocalPath.build("storage", "old")))
        val h = harness(scanPaths = seeded.paths)

        h.vm.resetScanPaths()
        advanceUntilIdle()

        val captured = slot<(DeduplicatorSettings.ScanPaths) -> DeduplicatorSettings.ScanPaths?>()
        coVerify(exactly = 1) { h.values.scanPaths.update(capture(captured)) }
        // Invoking the captured transform on the *seeded* prior value must yield the empty
        // ScanPaths. A no-op transform (`{ it }`) would return the seeded value and flunk this.
        captured.captured(seeded) shouldBe DeduplicatorSettings.ScanPaths()
    }

    @Test
    fun `picker result REPLACES scanPaths with the selected set (does not merge)`() = runTest2 {
        // Seed an existing scan path so a regression that merges/appends would surface here.
        val old = LocalPath.build("storage", "old")
        val newA = LocalPath.build("storage", "selected", "a")
        val newB = LocalPath.build("storage", "selected", "b")
        val h = harness(scanPaths = setOf(old))
        h.vm.state.first() // prime init flow

        h.pickerResults.value = PickerResult(selectedPaths = setOf(newA, newB))
        advanceUntilIdle()

        val captured = slot<(DeduplicatorSettings.ScanPaths) -> DeduplicatorSettings.ScanPaths?>()
        coVerify(exactly = 1) { h.values.scanPaths.update(capture(captured)) }
        // Invoke the captured transform with the seeded *non-empty* value. Result must be the
        // selected set verbatim — no `old`.
        val transformed = captured.captured(DeduplicatorSettings.ScanPaths(paths = setOf(old)))
        transformed?.paths shouldBe setOf(newA, newB)
    }
}
