package eu.darken.sdmse.appcontrol.ui.settings

import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class AppControlSettingsViewModelTest : BaseTest() {

    // ─────────────────────────── helpers ───────────────────────────

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private fun appControlState(
        canInfoSize: Boolean = true,
        canInfoActive: Boolean = true,
        canIncludeMultiUser: Boolean = true,
    ): AppControl.State = AppControl.State(
        data = null,
        progress = null as Progress.Data?,
        canInfoActive = canInfoActive,
        canInfoSize = canInfoSize,
        canInfoScreenTime = false,
        canToggle = false,
        canForceStop = false,
        canIncludeMultiUser = canIncludeMultiUser,
        canArchive = false,
        canRestore = false,
    )

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { type } returns UpgradeRepo.Type.FOSS
        every { upgradedAt } returns null
        every { error } returns null
    }

    private class Harness(
        val vm: AppControlSettingsViewModel,
        val settings: AppControlSettings,
        val moduleSizingEnabled: DataStoreValue<Boolean>,
        val moduleActivityEnabled: DataStoreValue<Boolean>,
        val includeMultiUserEnabled: DataStoreValue<Boolean>,
        val listSort: DataStoreValue<SortSettings>,
        val listFilter: DataStoreValue<FilterSettings>,
        val sizingFlow: MutableStateFlow<Boolean>,
        val activityFlow: MutableStateFlow<Boolean>,
    )

    private fun harness(
        isPro: Boolean = true,
        sizingEnabled: Boolean = true,
        activityEnabled: Boolean = true,
        multiUserEnabled: Boolean = false,
        canInfoSize: Boolean = true,
        canInfoActive: Boolean = true,
        canIncludeMultiUser: Boolean = true,
        listSortInitial: SortSettings = SortSettings(),
        listFilterInitial: FilterSettings = FilterSettings(),
    ): Harness {
        val sizingFlow = MutableStateFlow(sizingEnabled)
        val activityFlow = MutableStateFlow(activityEnabled)

        val moduleSizingValue = rwDataStoreValue(sizingEnabled, sizingFlow)
        val moduleActivityValue = rwDataStoreValue(activityEnabled, activityFlow)
        val includeMultiUserValue = rwDataStoreValue(multiUserEnabled)
        val listSortValue = rwDataStoreValue(listSortInitial)
        val listFilterValue = rwDataStoreValue(listFilterInitial)

        val settings = mockk<AppControlSettings>().apply {
            every { moduleSizingEnabled } returns moduleSizingValue
            every { moduleActivityEnabled } returns moduleActivityValue
            every { includeMultiUserEnabled } returns includeMultiUserValue
            every { listSort } returns listSortValue
            every { listFilter } returns listFilterValue
        }
        val appControl = mockk<AppControl>().apply {
            every { state } returns flowOf(
                appControlState(
                    canInfoSize = canInfoSize,
                    canInfoActive = canInfoActive,
                    canIncludeMultiUser = canIncludeMultiUser,
                ),
            )
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns MutableStateFlow(upgradeInfo(isPro = isPro))
        }

        val vm = AppControlSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            appControl = appControl,
            upgradeRepo = upgradeRepo,
            settings = settings,
        )
        return Harness(
            vm = vm,
            settings = settings,
            moduleSizingEnabled = moduleSizingValue,
            moduleActivityEnabled = moduleActivityValue,
            includeMultiUserEnabled = includeMultiUserValue,
            listSort = listSortValue,
            listFilter = listFilterValue,
            sizingFlow = sizingFlow,
            activityFlow = activityFlow,
        )
    }

    // ─────────────────────────── state surface ───────────────────────────

    @Test
    fun `state reflects pro-capable ready environment`() = runTest2 {
        val state = harness().vm.state.first()
        state.isPro shouldBe true
        state.canInfoSize shouldBe true
        state.canIncludeMultiUser shouldBe true
    }

    @Test
    fun `state reflects non-pro with no setup capabilities`() = runTest2 {
        val state = harness(
            isPro = false,
            canInfoSize = false,
            canInfoActive = false,
            canIncludeMultiUser = false,
        ).vm.state.first()
        state.isPro shouldBe false
        state.canInfoSize shouldBe false
        state.canInfoActive shouldBe false
        state.canIncludeMultiUser shouldBe false
    }

    @Test
    fun `state surfaces stored toggle values`() = runTest2 {
        val state = harness(
            sizingEnabled = false,
            activityEnabled = false,
            multiUserEnabled = true,
        ).vm.state.first()
        state.sizingEnabled shouldBe false
        state.activityEnabled shouldBe false
        state.multiUserEnabled shouldBe true
    }

    // ─────────────────────────── toggle writes ───────────────────────────

    @Test
    fun `setSizingEnabled writes through to module sizing DataStore`() = runTest2 {
        val h = harness(sizingEnabled = true)

        h.vm.setSizingEnabled(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.moduleSizingEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setActivityEnabled writes through to module activity DataStore`() = runTest2 {
        val h = harness(activityEnabled = true)

        h.vm.setActivityEnabled(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.moduleActivityEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setMultiUserEnabled writes through when user is Pro`() = runTest2 {
        val h = harness(isPro = true, multiUserEnabled = false)
        // Prime the state so isPro is observed before the setter runs (the setter reads
        // state.value.isPro to gate the write).
        h.vm.state.first()

        h.vm.setMultiUserEnabled(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.includeMultiUserEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setMultiUserEnabled is blocked when user is not Pro`() = runTest2 {
        // Defence-in-depth: even if the UI exposes the toggle, the VM must refuse to write the
        // value for non-Pro users.
        val h = harness(isPro = false, multiUserEnabled = false)
        h.vm.state.first()

        h.vm.setMultiUserEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.includeMultiUserEnabled.update(any()) }
    }

    // ─────────────────────────── badge navigation ───────────────────────────

    @Test
    fun `onSizingBadgeClick routes to setup with USAGE_STATS and STORAGE filter`() = runTest2 {
        // canInfoSize requires both usage stats and storage setup, so the badge must offer both.
        val h = harness()

        h.vm.onSizingBadgeClick()
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination
        route.shouldBeInstanceOf<SetupRoute>()
        route.options?.typeFilter shouldBe setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.STORAGE)
    }

    @Test
    fun `onActivityBadgeClick routes to setup with USAGE_STATS filter`() = runTest2 {
        val h = harness()

        h.vm.onActivityBadgeClick()
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination
        route.shouldBeInstanceOf<SetupRoute>()
        route.options?.typeFilter shouldBe setOf(SetupModule.Type.USAGE_STATS)
    }

    @Test
    fun `onMultiUserBadgeClick routes to UpgradeRoute when user is not Pro`() = runTest2 {
        val h = harness(isPro = false)
        h.vm.state.first()

        h.vm.onMultiUserBadgeClick()
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination
        route.shouldBeInstanceOf<UpgradeRoute>()
        route.forced shouldBe true
    }

    @Test
    fun `onMultiUserBadgeClick routes to setup ROOT+SHIZUKU when user is Pro`() = runTest2 {
        val h = harness(isPro = true)
        h.vm.state.first()

        h.vm.onMultiUserBadgeClick()
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination
        route.shouldBeInstanceOf<SetupRoute>()
        route.options?.typeFilter shouldBe setOf(SetupModule.Type.ROOT, SetupModule.Type.SHIZUKU)
    }

    // ─────────────────────────── flow side-effects ───────────────────────────

    @Test
    fun `disabling sizing while size-sort is active resets the sort to default`() = runTest2 {
        // Regression: when the user disables the sizing module, the VM clears any active size
        // sort so the list stops trying to render by-size with no data.
        val h = harness(
            sizingEnabled = true,
            listSortInitial = SortSettings(mode = SortSettings.Mode.SIZE, reversed = false),
        )

        // Flip the sizing flow → off → triggers the onEach hook.
        h.sizingFlow.value = false
        advanceUntilIdle()

        val captured = slot<(SortSettings) -> SortSettings?>()
        coVerify(exactly = 1) { h.listSort.update(capture(captured)) }
        // The setter passes a constant SortSettings() to .value(T), which expands to
        // .update { newValue }. The transformer returns the new SortSettings regardless of input.
        captured.captured(SortSettings(mode = SortSettings.Mode.SIZE, reversed = false)) shouldBe SortSettings()
    }

    @Test
    fun `disabling sizing does NOT reset sort when sort is not SIZE`() = runTest2 {
        val h = harness(
            sizingEnabled = true,
            listSortInitial = SortSettings(mode = SortSettings.Mode.NAME, reversed = false),
        )

        h.sizingFlow.value = false
        advanceUntilIdle()

        // No reset call — current sort mode is not SIZE.
        coVerify(exactly = 0) { h.listSort.update(any()) }
    }

    @Test
    fun `disabling activity while ACTIVE filter is set removes the ACTIVE tag`() = runTest2 {
        // Regression: disabling the activity module must scrub the ACTIVE filter tag so the list
        // doesn't try to filter by a column that's no longer being populated.
        val h = harness(
            activityEnabled = true,
            listFilterInitial = FilterSettings(
                tags = setOf(FilterSettings.Tag.USER, FilterSettings.Tag.ACTIVE),
            ),
        )

        h.activityFlow.value = false
        advanceUntilIdle()

        val captured = slot<(FilterSettings) -> FilterSettings?>()
        coVerify(exactly = 1) { h.listFilter.update(capture(captured)) }
        val newFilter = captured.captured(
            FilterSettings(tags = setOf(FilterSettings.Tag.USER, FilterSettings.Tag.ACTIVE)),
        )
        // ACTIVE removed, USER preserved.
        newFilter shouldBe FilterSettings(tags = setOf(FilterSettings.Tag.USER))
    }

    @Test
    fun `disabling activity does NOT touch the filter when ACTIVE is not set`() = runTest2 {
        val h = harness(
            activityEnabled = true,
            listFilterInitial = FilterSettings(tags = setOf(FilterSettings.Tag.USER)),
        )

        h.activityFlow.value = false
        advanceUntilIdle()

        coVerify(exactly = 0) { h.listFilter.update(any()) }
    }
}
