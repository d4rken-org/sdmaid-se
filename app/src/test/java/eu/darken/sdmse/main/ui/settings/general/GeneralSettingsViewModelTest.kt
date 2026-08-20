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
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.motd.MotdSettings
import io.kotest.assertions.withClue
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
 * Render state and writes of [GeneralSettingsViewModel], focused on the "show summary automatically"
 * setting: its state field maps from the right settings flow (the state combine is positional, so a
 * neighbouring boolean landing in the wrong slot is the failure mode) and its toggle writes it.
 */
internal class GeneralSettingsViewModelTest : BaseTest() {

    private class Harness(
        val vm: GeneralSettingsViewModel,
        val autoShowSetting: DataStoreValue<Boolean>,
        val cleanShortcutSettings: Map<CleanTool, DataStoreValue<Boolean>>,
    )

    /** The four tools that can have a per-tool clean shortcut. */
    private enum class CleanTool { CORPSEFINDER, SYSTEMCLEANER, APPCLEANER, DEDUPLICATOR }

    private fun GeneralSettingsViewModel.State.cleanShortcut(tool: CleanTool): Boolean = when (tool) {
        CleanTool.CORPSEFINDER -> shortcutCleanCorpseFinderEnabled
        CleanTool.SYSTEMCLEANER -> shortcutCleanSystemCleanerEnabled
        CleanTool.APPCLEANER -> shortcutCleanAppCleanerEnabled
        CleanTool.DEDUPLICATOR -> shortcutCleanDeduplicatorEnabled
    }

    private fun GeneralSettingsViewModel.setCleanShortcut(tool: CleanTool, enabled: Boolean) = when (tool) {
        CleanTool.CORPSEFINDER -> setShortcutCleanCorpseFinder(enabled)
        CleanTool.SYSTEMCLEANER -> setShortcutCleanSystemCleaner(enabled)
        CleanTool.APPCLEANER -> setShortcutCleanAppCleaner(enabled)
        CleanTool.DEDUPLICATOR -> setShortcutCleanDeduplicator(enabled)
    }

    /** Relaxed so `.value(x)` (which writes via `update {}`) answers and can be verified. */
    private fun mockBool(value: Boolean): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(value)
    }

    // TestScope extension: safeStateIn uses WhileSubscribed(5000), so without a live subscriber
    // vm.state stays at its initialValue and every assertion below would read State() defaults.
    private fun TestScope.harness(
        summaryAutoShow: Boolean = true,
        cleanShortcutOn: CleanTool? = null,
    ): Harness {
        val autoShowSetting = mockBool(summaryAutoShow)
        // Only the named tool is on: the state combine is positional, so this is what catches a
        // value landing in a neighbouring slot.
        val cleanSettings = CleanTool.entries.associateWith { mockBool(it == cleanShortcutOn) }
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { shortcutCleanCorpseFinderEnabled } returns cleanSettings.getValue(CleanTool.CORPSEFINDER)
            every { shortcutCleanSystemCleanerEnabled } returns cleanSettings.getValue(CleanTool.SYSTEMCLEANER)
            every { shortcutCleanAppCleanerEnabled } returns cleanSettings.getValue(CleanTool.APPCLEANER)
            every { shortcutCleanDeduplicatorEnabled } returns cleanSettings.getValue(CleanTool.DEDUPLICATOR)
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

        return Harness(vm = vm, autoShowSetting = autoShowSetting, cleanShortcutSettings = cleanSettings)
    }

    @Test
    fun `dashboardSummaryAutoShow maps from the auto-show setting`() = runTest2 {
        // Every neighbouring boolean is deliberately true, so the false can only come from the flow
        // it is supposed to come from — the 17-input combine is positional.
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
    fun `every clean shortcut state field maps from its own settings flow`() = runTest2 {
        CleanTool.entries.forEach { tool ->
            val h = harness(cleanShortcutOn = tool)
            advanceUntilIdle()

            val state = h.vm.state.first()
            withClue("$tool should be the only enabled clean shortcut") {
                CleanTool.entries.associateWith { state.cleanShortcut(it) } shouldBe
                        CleanTool.entries.associateWith { it == tool }
            }
            // Neighbouring slots in the positional combine stay where they belong.
            state.dashboardSummaryAutoShow shouldBe true
            state.widgetOneClickEnabled shouldBe true
        }
    }

    @Test
    fun `every clean shortcut setter writes only its own setting`() = runTest2 {
        CleanTool.entries.forEach { tool ->
            val h = harness()

            h.vm.setCleanShortcut(tool, true)
            advanceUntilIdle()

            withClue("$tool setter must not write another tool's setting") {
                h.cleanShortcutSettings.forEach { (candidate, setting) ->
                    coVerify(exactly = if (candidate == tool) 1 else 0) { setting.update(any()) }
                }
            }
        }
    }
}
