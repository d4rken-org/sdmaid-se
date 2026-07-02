package eu.darken.sdmse.corpsefinder.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class CorpseFinderSettingsViewModelTest : BaseTest() {

    // The `.value(value: T)` setter is an extension that delegates to `DataStoreValue.update`.
    // Stub `update` to a no-op success so MockK can both succeed the call and verify it later.
    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private fun cfState(rooted: Boolean = false): CorpseFinder.State = CorpseFinder.State(
        data = null,
        progress = null as Progress.Data?,
        isFilterPrivateDataAvailable = rooted,
        isFilterDalvikCacheAvailable = rooted,
        isFilterArtProfilesAvailable = rooted,
        isFilterAppLibrariesAvailable = rooted,
        isFilterAppSourcesAvailable = rooted,
        isFilterPrivateAppSourcesAvailable = rooted,
        isFilterEncryptedAppResourcesAvailable = rooted,
    )

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
    }

    private class Values(
        val isWatcherEnabled: DataStoreValue<Boolean>,
        val isWatcherAutoDeleteEnabled: DataStoreValue<Boolean>,
        val includeRiskKeeper: DataStoreValue<Boolean>,
        val includeRiskCommon: DataStoreValue<Boolean>,
        val filterSdcardEnabled: DataStoreValue<Boolean>,
        val filterPublicMediaEnabled: DataStoreValue<Boolean>,
        val filterPublicDataEnabled: DataStoreValue<Boolean>,
        val filterPublicObbEnabled: DataStoreValue<Boolean>,
        val filterPrivateDataEnabled: DataStoreValue<Boolean>,
        val filterDalvikCacheEnabled: DataStoreValue<Boolean>,
        val filterArtProfilesEnabled: DataStoreValue<Boolean>,
        val filterAppLibEnabled: DataStoreValue<Boolean>,
        val filterAppSourceEnabled: DataStoreValue<Boolean>,
        val filterAppSourcePrivateEnabled: DataStoreValue<Boolean>,
        val filterAppSourceAsecEnabled: DataStoreValue<Boolean>,
    )

    private class Harness(
        val vm: CorpseFinderSettingsViewModel,
        val context: Context,
        val packageManager: PackageManager,
        val settings: CorpseFinderSettings,
        val values: Values,
        val watcherFlow: MutableStateFlow<Boolean>,
    )

    private fun harness(
        isPro: Boolean = false,
        isWatcherEnabled: Boolean = false,
        isWatcherAutoDeleteEnabled: Boolean = true,
        includeRiskKeeper: Boolean = false,
        includeRiskCommon: Boolean = false,
        filterSdcard: Boolean = true,
        filterPublicMedia: Boolean = true,
        filterPublicData: Boolean = true,
        filterPublicObb: Boolean = false,
        filterPrivateData: Boolean = true,
        filterDalvikCache: Boolean = false,
        filterArtProfiles: Boolean = false,
        filterAppLib: Boolean = false,
        filterAppSource: Boolean = false,
        filterAppSourcePrivate: Boolean = false,
        filterAppSourceAsec: Boolean = false,
        rootAccess: AccessState = AccessState.Undecided,
        cfState: CorpseFinder.State = cfState(),
    ): Harness {
        val context = mockk<Context>().apply {
            every { packageName } returns "eu.darken.sdmse.corpsefinder.test"
        }
        val packageManager = mockk<PackageManager>(relaxed = true)
        val watcherFlow = MutableStateFlow(isWatcherEnabled)
        val values = Values(
            isWatcherEnabled = rwDataStoreValue(isWatcherEnabled, watcherFlow),
            isWatcherAutoDeleteEnabled = rwDataStoreValue(isWatcherAutoDeleteEnabled),
            includeRiskKeeper = rwDataStoreValue(includeRiskKeeper),
            includeRiskCommon = rwDataStoreValue(includeRiskCommon),
            filterSdcardEnabled = rwDataStoreValue(filterSdcard),
            filterPublicMediaEnabled = rwDataStoreValue(filterPublicMedia),
            filterPublicDataEnabled = rwDataStoreValue(filterPublicData),
            filterPublicObbEnabled = rwDataStoreValue(filterPublicObb),
            filterPrivateDataEnabled = rwDataStoreValue(filterPrivateData),
            filterDalvikCacheEnabled = rwDataStoreValue(filterDalvikCache),
            filterArtProfilesEnabled = rwDataStoreValue(filterArtProfiles),
            filterAppLibEnabled = rwDataStoreValue(filterAppLib),
            filterAppSourceEnabled = rwDataStoreValue(filterAppSource),
            filterAppSourcePrivateEnabled = rwDataStoreValue(filterAppSourcePrivate),
            filterAppSourceAsecEnabled = rwDataStoreValue(filterAppSourceAsec),
        )
        val settings = mockk<CorpseFinderSettings>().apply {
            every { this@apply.isWatcherEnabled } returns values.isWatcherEnabled
            every { this@apply.isWatcherAutoDeleteEnabled } returns values.isWatcherAutoDeleteEnabled
            every { this@apply.includeRiskKeeper } returns values.includeRiskKeeper
            every { this@apply.includeRiskCommon } returns values.includeRiskCommon
            every { this@apply.filterSdcardEnabled } returns values.filterSdcardEnabled
            every { this@apply.filterPublicMediaEnabled } returns values.filterPublicMediaEnabled
            every { this@apply.filterPublicDataEnabled } returns values.filterPublicDataEnabled
            every { this@apply.filterPublicObbEnabled } returns values.filterPublicObbEnabled
            every { this@apply.filterPrivateDataEnabled } returns values.filterPrivateDataEnabled
            every { this@apply.filterDalvikCacheEnabled } returns values.filterDalvikCacheEnabled
            every { this@apply.filterArtProfilesEnabled } returns values.filterArtProfilesEnabled
            every { this@apply.filterAppLibEnabled } returns values.filterAppLibEnabled
            every { this@apply.filterAppSourceEnabled } returns values.filterAppSourceEnabled
            every { this@apply.filterAppSourcePrivateEnabled } returns values.filterAppSourcePrivateEnabled
            every { this@apply.filterAppSourceAsecEnabled } returns values.filterAppSourceAsecEnabled
        }
        val corpseFinder = mockk<CorpseFinder>().apply {
            every { state } returns flowOf(cfState)
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
            every { isSettled } returns flowOf(true)
        }
        val rootManager = mockk<RootManager>().apply {
            every { accessState } returns flowOf(rootAccess)
        }
        val vm = CorpseFinderSettingsViewModel(
            context = context,
            packageManager = packageManager,
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
            upgradeRepo = upgradeRepo,
            corpseFinder = corpseFinder,
            rootManager = rootManager,
        )
        return Harness(vm, context, packageManager, settings, values, watcherFlow)
    }

    @Test
    fun `state passes through DataStore values when seeded with the production defaults`() = runTest2 {
        // The harness defaults mirror the production defaults declared in CorpseFinderSettings.
        // This test verifies the VM's combine() correctly threads each value into State; it
        // does NOT validate the production defaults themselves (those would need a real
        // DataStore). If CorpseFinderSettings flips a default, update this harness alongside.
        val h = harness()

        val state = h.vm.state.first()
        state.isPro shouldBe false
        state.isWatcherEnabled shouldBe false
        state.isWatcherAutoDeleteEnabled shouldBe true
        state.includeRiskKeeper shouldBe false
        state.includeRiskCommon shouldBe false
        state.filterSdcardEnabled shouldBe true
        state.filterPublicMediaEnabled shouldBe true
        state.filterPublicDataEnabled shouldBe true
        state.filterPublicObbEnabled shouldBe false
        state.filterPrivateDataEnabled shouldBe true
        state.filterDalvikCacheEnabled shouldBe false
        state.filterArtProfilesEnabled shouldBe false
        state.filterAppLibEnabled shouldBe false
        state.filterAppSourceEnabled shouldBe false
        state.filterAppSourcePrivateEnabled shouldBe false
        state.filterAppSourceAsecEnabled shouldBe false
    }

    @Test
    fun `state reflects non-default DataStore values`() = runTest2 {
        val h = harness(
            isPro = true,
            isWatcherEnabled = true,
            isWatcherAutoDeleteEnabled = false,
            includeRiskKeeper = true,
            includeRiskCommon = true,
            filterDalvikCache = true,
            filterArtProfiles = true,
            filterAppLib = true,
            filterAppSource = true,
            filterAppSourcePrivate = true,
            filterAppSourceAsec = true,
        )

        val state = h.vm.state.first()
        state.isPro shouldBe true
        state.isWatcherEnabled shouldBe true
        state.isWatcherAutoDeleteEnabled shouldBe false
        state.includeRiskKeeper shouldBe true
        state.includeRiskCommon shouldBe true
        state.filterDalvikCacheEnabled shouldBe true
        state.filterArtProfilesEnabled shouldBe true
        state.filterAppLibEnabled shouldBe true
        state.filterAppSourceEnabled shouldBe true
        state.filterAppSourcePrivateEnabled shouldBe true
        state.filterAppSourceAsecEnabled shouldBe true
    }

    @Test
    fun `state surfaces root-gated filter availability from CorpseFinder state`() = runTest2 {
        val h = harness(cfState = cfState(rooted = true))

        val state = h.vm.state.first()
        state.isFilterPrivateDataAvailable shouldBe true
    }

    @Test
    fun `setWatcherEnabled is blocked when not pro`() = runTest2 {
        val h = harness(isPro = false)
        h.vm.state.first() // prime state so isPro is observed

        h.vm.setWatcherEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.values.isWatcherEnabled.update(any()) }
    }

    @Test
    fun `setWatcherEnabled writes through when pro`() = runTest2 {
        val h = harness(isPro = true)
        h.vm.state.first()

        h.vm.setWatcherEnabled(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.isWatcherEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeRiskKeeper writes through`() = runTest2 {
        val h = harness()

        h.vm.setIncludeRiskKeeper(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeRiskKeeper.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeRiskCommon writes through`() = runTest2 {
        val h = harness()

        h.vm.setIncludeRiskCommon(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeRiskCommon.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setFilterPrivateDataEnabled writes through`() = runTest2 {
        val h = harness()

        h.vm.setFilterPrivateDataEnabled(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.filterPrivateDataEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setFilterDalvikCacheEnabled writes through`() = runTest2 {
        val h = harness()

        h.vm.setFilterDalvikCacheEnabled(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.filterDalvikCacheEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setWatcherAutoDeleteEnabled writes through`() = runTest2 {
        val h = harness()

        h.vm.setWatcherAutoDeleteEnabled(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.isWatcherAutoDeleteEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `init does not toggle watcher receiver for the initial flow replay`() = runTest2 {
        val h = harness(isWatcherEnabled = false)

        // VM init subscribes to isWatcherEnabled.flow with drop(1). The initial replay must NOT
        // trigger the receiver toggle, even after the test scope drains.
        advanceUntilIdle()

        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }
    }

    // NOTE: A full assertion that subsequent flow emissions DO toggle the receiver isn't possible
    // under stock JVM tests because the production code constructs `ComponentName(context, ...)`
    // and ComponentName's constructor + hashCode() are stubbed (throw "Stub!") in android.jar
    // without Robolectric. Moving the test to a Robolectric class would force JUnit 4 lifecycle
    // annotations that conflict with the JUnit 5 + BaseTest pattern used elsewhere here. The
    // drop(1) behaviour is still covered by the test above; user-driven writes through
    // setWatcherEnabled are covered by `setWatcherEnabled writes through when pro`.

}
