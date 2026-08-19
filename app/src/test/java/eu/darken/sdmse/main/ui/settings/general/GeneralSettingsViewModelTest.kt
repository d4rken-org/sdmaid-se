package eu.darken.sdmse.main.ui.settings.general

import android.os.LocaleList
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.locale.LocaleManager
import eu.darken.sdmse.common.theming.ThemeColor
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeStyle
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.ShortcutConfig
import eu.darken.sdmse.main.core.motd.MotdSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue

/**
 * Render state and writes of [GeneralSettingsViewModel]: state fields map from the right settings
 * flow (the state combine is positional, so a neighbouring value landing in the wrong slot is the
 * failure mode), and the toggles write what they claim to write.
 */
internal class GeneralSettingsViewModelTest : BaseTest() {

    private class Harness(
        val vm: GeneralSettingsViewModel,
        val autoShowSetting: DataStoreValue<Boolean>,
        val shortcutTools: () -> List<DashboardCardType>,
    )

    /** Relaxed so `.value(x)` (which writes via `update {}`) answers and can be verified. */
    private fun mockBool(value: Boolean): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(value)
    }

    /**
     * State-backed so `update {}` composes the way the real DataStore does. A plain relaxed mock
     * would silently swallow a lost update, which is exactly what the write path must not do.
     */
    private fun mockShortcutConfig(
        state: MutableStateFlow<ShortcutConfig>,
    ): DataStoreValue<ShortcutConfig> = mockk<DataStoreValue<ShortcutConfig>>(relaxed = true).apply {
        every { flow } returns state
        coEvery { update(any()) } answers {
            val transform = firstArg<(ShortcutConfig) -> ShortcutConfig?>()
            val old = state.value
            val new = transform(old) ?: ShortcutConfig()
            state.value = new
            DataStoreValue.Updated(old = old, new = new)
        }
    }

    // TestScope extension: safeStateIn uses WhileSubscribed(5000), so without a live subscriber
    // vm.state stays at its initialValue and every assertion below would read State() defaults.
    private fun TestScope.harness(
        summaryAutoShow: Boolean = true,
        shortcutTools: ShortcutConfig = ShortcutConfig(),
        cardConfig: DashboardCardConfig = DashboardCardConfig(),
    ): Harness {
        val autoShowSetting = mockBool(summaryAutoShow)
        val shortcutToolState = MutableStateFlow(shortcutTools)
        val shortcutToolSetting = mockShortcutConfig(shortcutToolState)
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { enableDashboardOneClick } returns mockDataStoreValue(true)
            every { shortcutOneClickEnabled } returns mockDataStoreValue(true)
            every { themeMode } returns mockDataStoreValue(ThemeMode.SYSTEM)
            every { themeStyle } returns mockDataStoreValue(ThemeStyle.DEFAULT)
            every { themeColor } returns mockDataStoreValue(ThemeColor.GREEN)
            every { usePreviews } returns mockDataStoreValue(true)
            every { romTypeDetection } returns mockDataStoreValue(RomType.AUTO)
            every { isUpdateCheckEnabled } returns mockDataStoreValue(true)
            every { oneClickCorpseFinderEnabled } returns mockDataStoreValue(true)
            every { oneClickSystemCleanerEnabled } returns mockDataStoreValue(true)
            every { oneClickAppCleanerEnabled } returns mockDataStoreValue(true)
            every { oneClickDeduplicatorEnabled } returns mockDataStoreValue(true)
            every { widgetOneClickEnabled } returns mockDataStoreValue(true)
            every { dashboardHeroAutoShow } returns autoShowSetting
            every { shortcutToolConfig } returns shortcutToolSetting
            every { dashboardCardConfig } returns mockDataStoreValue(cardConfig)
        }
        val debugSettings = mockk<DebugSettings>(relaxed = true).apply {
            every { isDebugMode } returns mockDataStoreValue(true)
        }
        val motdSettings = mockk<MotdSettings>(relaxed = true).apply {
            every { isMotdEnabled } returns mockDataStoreValue(true)
        }
        val updateChecker = mockk<UpdateChecker>(relaxed = true).apply {
            coEvery { isCheckSupported() } returns true
        }
        val localeManager = mockk<LocaleManager>(relaxed = true).apply {
            // A mock, not LocaleList.getDefault(): the android.jar on the unit test classpath throws
            // on any real framework call.
            every { currentLocales } returns MutableStateFlow(mockk<LocaleList>(relaxed = true))
        }
        val proInfo = mockk<UpgradeRepo.Info>(relaxed = true).apply {
            every { isPro } returns true
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns MutableStateFlow(proInfo)
        }

        val vm = GeneralSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            upgradeRepo = upgradeRepo,
            generalSettings = generalSettings,
            debugSettings = debugSettings,
            motdSettings = motdSettings,
            updateChecker = updateChecker,
            localeManager = localeManager,
            guidedTourController = mockk<GuidedTourController>(relaxed = true),
        )

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.state.collect { /* keep WhileSubscribed alive */ }
        }

        return Harness(
            vm = vm,
            autoShowSetting = autoShowSetting,
            shortcutTools = { shortcutToolState.value.tools },
        )
    }

    @Test
    fun `dashboardSummaryAutoShow maps from the auto-show setting`() = runTest2 {
        // Every neighbouring boolean is deliberately true, so the false can only come from the flow
        // it is supposed to come from — the 19-input combine is positional.
        val h = harness(summaryAutoShow = false)
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.dashboardSummaryAutoShow shouldBe false
        state.enableDashboardOneClick shouldBe true
        state.shortcutOneClickEnabled shouldBe true
        state.widgetOneClickEnabled shouldBe true
    }

    @Test
    fun `dashboardSummaryAutoShow is on when the setting is on`() = runTest2 {
        val h = harness(summaryAutoShow = true)
        advanceUntilIdle()

        h.vm.state.first().dashboardSummaryAutoShow shouldBe true
    }

    @Test
    fun `toggleDashboardSummaryAutoShow writes the setting`() = runTest2 {
        val h = harness()

        h.vm.toggleDashboardSummaryAutoShow(false)
        advanceUntilIdle()

        // .value(false) is an extension that writes through update {}.
        coVerify(exactly = 1) { h.autoShowSetting.update(any()) }
    }

    @Test
    fun `shortcut tool state exposes the selection and the publish order`() = runTest2 {
        val h = harness(
            shortcutTools = ShortcutConfig(tools = listOf(DashboardCardType.APPCLEANER)),
            cardConfig = DashboardCardConfig(
                cards = listOf(
                    DashboardCardConfig.CardEntry(DashboardCardType.STATS),
                    DashboardCardConfig.CardEntry(DashboardCardType.APPCLEANER),
                ),
            ),
        )
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.shortcutToolsEnabled shouldBe setOf(DashboardCardType.APPCLEANER)
        // Stored order first, then the card types it doesn't mention, in enum order.
        state.shortcutTools.take(2) shouldBe listOf(DashboardCardType.STATS, DashboardCardType.APPCLEANER)
        state.shortcutTools.toSet() shouldBe DashboardCardType.entries.toSet()
    }

    @Test
    fun `setShortcutTool adds and removes a tool`() = runTest2 {
        val h = harness(shortcutTools = ShortcutConfig(tools = listOf(DashboardCardType.APPCONTROL)))

        h.vm.setShortcutTool(DashboardCardType.APPCLEANER, true)
        advanceUntilIdle()
        h.shortcutTools().toSet() shouldBe setOf(DashboardCardType.APPCONTROL, DashboardCardType.APPCLEANER)

        h.vm.setShortcutTool(DashboardCardType.APPCONTROL, false)
        advanceUntilIdle()
        h.shortcutTools() shouldBe listOf(DashboardCardType.APPCLEANER)
    }

    @Test
    fun `enabling an already enabled tool does not duplicate it`() = runTest2 {
        val h = harness(shortcutTools = ShortcutConfig(tools = listOf(DashboardCardType.APPCONTROL)))

        h.vm.setShortcutTool(DashboardCardType.APPCONTROL, true)
        advanceUntilIdle()

        h.shortcutTools() shouldBe listOf(DashboardCardType.APPCONTROL)
    }

    @Test
    fun `two concurrent toggles of different tools both survive`() = runTest2 {
        // VM operations run concurrently on the Default dispatcher. A read-then-write would let both
        // toggles read the same list, and the second write would drop the first.
        val h = harness(shortcutTools = ShortcutConfig(tools = emptyList()))

        h.vm.setShortcutTool(DashboardCardType.APPCLEANER, true)
        h.vm.setShortcutTool(DashboardCardType.CORPSEFINDER, true)
        advanceUntilIdle()

        h.shortcutTools().toSet() shouldBe setOf(DashboardCardType.APPCLEANER, DashboardCardType.CORPSEFINDER)
    }
}
