package eu.darken.sdmse.main.ui.settings.general

import android.os.LocaleList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AccessibilityNew
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.DarkMode
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.SystemUpdate
import androidx.compose.material.icons.twotone.TouchApp
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.locale.toList
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeStyle
import eu.darken.sdmse.main.ui.navigation.DashboardCardConfigRoute
import androidx.compose.ui.platform.LocalContext
import eu.darken.sdmse.common.R as CommonR

@Composable
fun GeneralSettingsScreenHost(
    vm: GeneralSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    GeneralSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onThemeModeChanged = vm::setThemeMode,
        onThemeStyleChanged = vm::setThemeStyle,
        onOneClickChanged = vm::toggleOneClick,
        onShortcutOneClickChanged = vm::toggleShortcutOneClick,
        onPreviewsChanged = vm::togglePreviews,
        onUpdateCheckChanged = vm::toggleUpdateCheck,
        onMotdChanged = vm::toggleMotd,
        onDebugChanged = vm::toggleDebugMode,
        onLanguageClick = vm::showLanguagePicker,
        onDashboardCardConfigClick = { vm.navTo(DashboardCardConfigRoute) },
        onThemeLockedClick = { vm.navTo(UpgradeRoute(forced = true)) },
        onRomTypeChanged = vm::setRomType,
        onOneClickCorpseFinderChanged = vm::setOneClickCorpseFinder,
        onOneClickSystemCleanerChanged = vm::setOneClickSystemCleaner,
        onOneClickAppCleanerChanged = vm::setOneClickAppCleaner,
        onOneClickDeduplicatorChanged = vm::setOneClickDeduplicator,
    )
}

@Composable
internal fun GeneralSettingsScreen(
    state: GeneralSettingsViewModel.State = GeneralSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onThemeStyleChanged: (ThemeStyle) -> Unit = {},
    onOneClickChanged: (Boolean) -> Unit = {},
    onShortcutOneClickChanged: (Boolean) -> Unit = {},
    onPreviewsChanged: (Boolean) -> Unit = {},
    onUpdateCheckChanged: (Boolean) -> Unit = {},
    onMotdChanged: (Boolean) -> Unit = {},
    onDebugChanged: (Boolean) -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onDashboardCardConfigClick: () -> Unit = {},
    onThemeLockedClick: () -> Unit = {},
    onRomTypeChanged: (RomType) -> Unit = {},
    onOneClickCorpseFinderChanged: (Boolean) -> Unit = {},
    onOneClickSystemCleanerChanged: (Boolean) -> Unit = {},
    onOneClickAppCleanerChanged: (Boolean) -> Unit = {},
    onOneClickDeduplicatorChanged: (Boolean) -> Unit = {},
) {
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showThemeStyleDialog by remember { mutableStateOf(false) }
    var showRomTypeDialog by remember { mutableStateOf(false) }
    var showOneClickTools by remember { mutableStateOf(false) }

    if (showThemeModeDialog) {
        ThemeModePickerDialog(
            currentMode = state.themeMode,
            onModeSelected = { mode ->
                onThemeModeChanged(mode)
                showThemeModeDialog = false
            },
            onDismiss = { showThemeModeDialog = false },
        )
    }

    if (showThemeStyleDialog) {
        ThemeStylePickerDialog(
            currentStyle = state.themeStyle,
            onStyleSelected = { style ->
                onThemeStyleChanged(style)
                showThemeStyleDialog = false
            },
            onDismiss = { showThemeStyleDialog = false },
        )
    }

    if (showRomTypeDialog) {
        RomTypePickerDialog(
            currentRomType = state.romTypeDetection,
            onRomTypeSelected = onRomTypeChanged,
            onDismiss = { showRomTypeDialog = false },
        )
    }

    if (showOneClickTools) {
        OneClickOptionsDialog(
            corpseFinderEnabled = state.oneClickCorpseFinderEnabled,
            systemCleanerEnabled = state.oneClickSystemCleanerEnabled,
            appCleanerEnabled = state.oneClickAppCleanerEnabled,
            deduplicatorEnabled = state.oneClickDeduplicatorEnabled,
            onCorpseFinderChanged = onOneClickCorpseFinderChanged,
            onSystemCleanerChanged = onOneClickSystemCleanerChanged,
            onAppCleanerChanged = onOneClickAppCleanerChanged,
            onDeduplicatorChanged = onOneClickDeduplicatorChanged,
            onDismiss = { showOneClickTools = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            // Dashboard category
            item { SettingsCategoryHeader(text = stringResource(R.string.dashboard_settings_category)) }

            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.TouchApp,
                    title = stringResource(R.string.dashboard_settings_oneclick_title),
                    subtitle = stringResource(R.string.dashboard_settings_oneclick_summary),
                    checked = state.enableDashboardOneClick,
                    onCheckedChange = onOneClickChanged,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.TwoTone.ViewList,
                    title = stringResource(R.string.dashboard_settings_oneclick_tools_title),
                    subtitle = stringResource(R.string.dashboard_settings_oneclick_tools_desc),
                    onClick = { showOneClickTools = true },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.GridView,
                    title = stringResource(R.string.dashboard_card_config_title),
                    subtitle = stringResource(R.string.dashboard_card_config_desc),
                    onClick = onDashboardCardConfigClick,
                )
            }

            // Shortcuts category
            item { SettingsCategoryHeader(text = stringResource(R.string.shortcuts_settings_category)) }

            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.TouchApp,
                    title = stringResource(R.string.shortcuts_onetap_enabled_title),
                    subtitle = stringResource(R.string.shortcuts_onetap_enabled_summary),
                    checked = state.shortcutOneClickEnabled,
                    onCheckedChange = onShortcutOneClickChanged,
                )
            }

            // UI category
            item { SettingsCategoryHeader(text = stringResource(R.string.settings_category_ui_label)) }

            item {
                val themeModeLabel = when (state.themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.ui_theme_mode_system_label)
                    ThemeMode.DARK -> stringResource(R.string.ui_theme_mode_dark_label)
                    ThemeMode.LIGHT -> stringResource(R.string.ui_theme_mode_light_label)
                }
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.DarkMode,
                    title = stringResource(R.string.ui_theme_mode_setting_label),
                    subtitle = stringResource(R.string.ui_theme_mode_setting_explanation),
                    value = themeModeLabel,
                    onClick = { showThemeModeDialog = true },
                    requiresUpgrade = !state.isPro,
                    onUpgrade = onThemeLockedClick,
                )
            }
            item {
                val themeStyleLabel = when (state.themeStyle) {
                    ThemeStyle.DEFAULT -> stringResource(R.string.ui_theme_style_default_label)
                    ThemeStyle.MATERIAL_YOU -> stringResource(R.string.ui_theme_style_materialyou_label)
                    ThemeStyle.MEDIUM_CONTRAST -> stringResource(R.string.ui_theme_style_mediumcontrast_label)
                    ThemeStyle.HIGH_CONTRAST -> stringResource(R.string.ui_theme_style_highcontrast_label)
                }
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Palette,
                    title = stringResource(R.string.ui_theme_style_setting_label),
                    subtitle = stringResource(R.string.ui_theme_style_setting_explanation),
                    value = themeStyleLabel,
                    onClick = { showThemeStyleDialog = true },
                    requiresUpgrade = !state.isPro,
                    onUpgrade = onThemeLockedClick,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Image,
                    title = stringResource(R.string.ui_previews_title),
                    subtitle = stringResource(R.string.ui_previews_summary),
                    checked = state.usePreviews,
                    onCheckedChange = onPreviewsChanged,
                )
            }
            if (state.showLanguage) {
                item {
                    val localeNames = state.currentLocales?.let { locales ->
                        locales.toList().firstOrNull()?.displayName?.ifEmpty { locales.toString() }
                    } ?: ""
                    SettingsPreferenceItem(
                        icon = Icons.TwoTone.Language,
                        title = stringResource(R.string.ui_language_override_label),
                        subtitle = stringResource(R.string.ui_language_override_desc, localeNames),
                        onClick = onLanguageClick,
                    )
                }
            }

            // Device category
            item { SettingsCategoryHeader(text = stringResource(R.string.device_settings_category)) }

            item {
                val context = LocalContext.current
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.AccessibilityNew,
                    title = stringResource(eu.darken.sdmse.appcleaner.R.string.appcleaner_automation_romtype_detection_label),
                    subtitle = stringResource(eu.darken.sdmse.appcleaner.R.string.appcleaner_automation_romtype_detection_summary),
                    value = state.romTypeDetection.label.get(context),
                    onClick = { showRomTypeDialog = true },
                )
            }

            // Other category
            item { SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label)) }

            if (state.isUpdateCheckSupported) {
                item {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.SystemUpdate,
                        title = stringResource(R.string.updatecheck_setting_enabled_label),
                        subtitle = stringResource(R.string.updatecheck_setting_enabled_explanation),
                        checked = state.isUpdateCheckEnabled,
                        onCheckedChange = onUpdateCheckChanged,
                    )
                }
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.TwoTone.Message,
                    title = stringResource(R.string.motd_setting_enabled_label),
                    subtitle = stringResource(R.string.motd_setting_enabled_explanation),
                    checked = state.isMotdEnabled,
                    onCheckedChange = onMotdChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.BugReport,
                    title = stringResource(R.string.debug_mode_label),
                    subtitle = stringResource(R.string.debug_mode_explanation),
                    checked = state.isDebugMode,
                    onCheckedChange = onDebugChanged,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreviewFree() {
    PreviewWrapper {
        GeneralSettingsScreen(state = GeneralSettingsViewModel.State(isPro = false))
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreviewPro() {
    PreviewWrapper {
        GeneralSettingsScreen(state = GeneralSettingsViewModel.State(isPro = true))
    }
}
