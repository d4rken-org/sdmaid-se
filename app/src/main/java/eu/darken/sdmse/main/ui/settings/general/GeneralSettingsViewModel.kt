package eu.darken.sdmse.main.ui.settings.general

import android.annotation.SuppressLint
import android.os.LocaleList
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.locale.LocaleManager
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeStyle
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.motd.MotdSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
    private val motdSettings: MotdSettings,
    private val updateChecker: UpdateChecker,
    private val localeManager: LocaleManager,
) : ViewModel4(dispatcherProvider, TAG) {

    val state: StateFlow<State> = combine(
        upgradeRepo.upgradeInfo.map { it.isPro },
        flow { emit(updateChecker.isCheckSupported()) },
        generalSettings.enableDashboardOneClick.flow,
        generalSettings.shortcutOneClickEnabled.flow,
        generalSettings.themeMode.flow,
        generalSettings.themeStyle.flow,
        generalSettings.usePreviews.flow,
        generalSettings.romTypeDetection.flow,
        generalSettings.isUpdateCheckEnabled.flow,
        motdSettings.isMotdEnabled.flow,
        debugSettings.isDebugMode.flow,
        localeManager.currentLocales,
        generalSettings.oneClickCorpseFinderEnabled.flow,
        generalSettings.oneClickSystemCleanerEnabled.flow,
        generalSettings.oneClickAppCleanerEnabled.flow,
        generalSettings.oneClickDeduplicatorEnabled.flow,
    ) { isPro, isUpdateCheckSupported, oneClick, shortcut, themeMode, themeStyle, previews, romType, updateCheck, motd, debug, locales,
        oneClickCorpseFinder, oneClickSystemCleaner, oneClickAppCleaner, oneClickDeduplicator ->
        State(
            isPro = isPro,
            isUpdateCheckSupported = isUpdateCheckSupported,
            enableDashboardOneClick = oneClick,
            shortcutOneClickEnabled = shortcut,
            themeMode = themeMode,
            themeStyle = themeStyle,
            usePreviews = previews,
            romTypeDetection = romType,
            isUpdateCheckEnabled = updateCheck,
            isMotdEnabled = motd,
            isDebugMode = debug,
            currentLocales = locales,
            showLanguage = hasApiLevel(33),
            oneClickCorpseFinderEnabled = oneClickCorpseFinder,
            oneClickSystemCleanerEnabled = oneClickSystemCleaner,
            oneClickAppCleanerEnabled = oneClickAppCleaner,
            oneClickDeduplicatorEnabled = oneClickDeduplicator,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun toggleOneClick(enabled: Boolean) = launch {
        generalSettings.enableDashboardOneClick.value(enabled)
    }

    fun toggleShortcutOneClick(enabled: Boolean) = launch {
        generalSettings.shortcutOneClickEnabled.value(enabled)
    }

    fun setThemeMode(mode: ThemeMode) = launch {
        generalSettings.themeMode.value(mode)
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        generalSettings.themeStyle.value(style)
    }

    fun togglePreviews(enabled: Boolean) = launch {
        generalSettings.usePreviews.value(enabled)
    }

    fun setRomType(romType: RomType) = launch {
        generalSettings.romTypeDetection.value(romType)
    }

    fun toggleUpdateCheck(enabled: Boolean) = launch {
        generalSettings.isUpdateCheckEnabled.value(enabled)
    }

    fun toggleMotd(enabled: Boolean) = launch {
        motdSettings.isMotdEnabled.value(enabled)
    }

    fun toggleDebugMode(enabled: Boolean) = launch {
        debugSettings.isDebugMode.value(enabled)
    }

    fun setOneClickCorpseFinder(enabled: Boolean) = launch {
        generalSettings.oneClickCorpseFinderEnabled.value(enabled)
    }

    fun setOneClickSystemCleaner(enabled: Boolean) = launch {
        generalSettings.oneClickSystemCleanerEnabled.value(enabled)
    }

    fun setOneClickAppCleaner(enabled: Boolean) = launch {
        generalSettings.oneClickAppCleanerEnabled.value(enabled)
    }

    fun setOneClickDeduplicator(enabled: Boolean) = launch {
        generalSettings.oneClickDeduplicatorEnabled.value(enabled)
    }

    @SuppressLint("NewApi")
    fun showLanguagePicker() = launch {
        log(TAG) { "showLanguagePicker()" }
        if (hasApiLevel(33)) {
            localeManager.showLanguagePicker()
        } else {
            throw IllegalStateException("This should not be clickable below API 33...")
        }
    }

    data class State(
        val isPro: Boolean = false,
        val isUpdateCheckSupported: Boolean = false,
        val enableDashboardOneClick: Boolean = false,
        val shortcutOneClickEnabled: Boolean = false,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val themeStyle: ThemeStyle = ThemeStyle.DEFAULT,
        val usePreviews: Boolean = true,
        val romTypeDetection: RomType = RomType.AUTO,
        val isUpdateCheckEnabled: Boolean = false,
        val isMotdEnabled: Boolean = true,
        val isDebugMode: Boolean = false,
        val currentLocales: LocaleList? = null,
        val showLanguage: Boolean = false,
        val oneClickCorpseFinderEnabled: Boolean = true,
        val oneClickSystemCleanerEnabled: Boolean = true,
        val oneClickAppCleanerEnabled: Boolean = true,
        val oneClickDeduplicatorEnabled: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "General", "ViewModel")
    }
}
