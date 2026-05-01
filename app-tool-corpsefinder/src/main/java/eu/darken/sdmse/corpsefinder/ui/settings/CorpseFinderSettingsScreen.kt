package eu.darken.sdmse.corpsefinder.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.LocalLibrary
import androidx.compose.material.icons.twotone.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingGate
import eu.darken.sdmse.common.compose.settings.SettingsBadgedSwitchItem
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.corpsefinder.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ui.R as UiR

@Composable
fun CorpseFinderSettingsScreenHost(
    vm: CorpseFinderSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    CorpseFinderSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onWatcherChanged = vm::setWatcherEnabled,
        onWatcherBadgeClick = vm::onWatcherBadgeClick,
        onWatcherAutoDeleteChanged = vm::setWatcherAutoDeleteEnabled,
        onIncludeRiskKeeperChanged = vm::setIncludeRiskKeeper,
        onIncludeRiskCommonChanged = vm::setIncludeRiskCommon,
        onFilterSdcardChanged = vm::setFilterSdcardEnabled,
        onFilterPublicMediaChanged = vm::setFilterPublicMediaEnabled,
        onFilterPublicDataChanged = vm::setFilterPublicDataEnabled,
        onFilterPublicObbChanged = vm::setFilterPublicObbEnabled,
        onFilterPrivateDataChanged = vm::setFilterPrivateDataEnabled,
        onFilterDalvikCacheChanged = vm::setFilterDalvikCacheEnabled,
        onFilterArtProfilesChanged = vm::setFilterArtProfilesEnabled,
        onFilterAppLibChanged = vm::setFilterAppLibEnabled,
        onFilterAppSourceChanged = vm::setFilterAppSourceEnabled,
        onFilterAppSourcePrivateChanged = vm::setFilterAppSourcePrivateEnabled,
        onFilterAppSourceAsecChanged = vm::setFilterAppSourceAsecEnabled,
        onRootFilterBadgeClick = vm::onRootFilterBadgeClick,
    )
}

@Composable
internal fun CorpseFinderSettingsScreen(
    state: CorpseFinderSettingsViewModel.State = CorpseFinderSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onWatcherChanged: (Boolean) -> Unit = {},
    onWatcherBadgeClick: () -> Unit = {},
    onWatcherAutoDeleteChanged: (Boolean) -> Unit = {},
    onIncludeRiskKeeperChanged: (Boolean) -> Unit = {},
    onIncludeRiskCommonChanged: (Boolean) -> Unit = {},
    onFilterSdcardChanged: (Boolean) -> Unit = {},
    onFilterPublicMediaChanged: (Boolean) -> Unit = {},
    onFilterPublicDataChanged: (Boolean) -> Unit = {},
    onFilterPublicObbChanged: (Boolean) -> Unit = {},
    onFilterPrivateDataChanged: (Boolean) -> Unit = {},
    onFilterDalvikCacheChanged: (Boolean) -> Unit = {},
    onFilterArtProfilesChanged: (Boolean) -> Unit = {},
    onFilterAppLibChanged: (Boolean) -> Unit = {},
    onFilterAppSourceChanged: (Boolean) -> Unit = {},
    onFilterAppSourcePrivateChanged: (Boolean) -> Unit = {},
    onFilterAppSourceAsecChanged: (Boolean) -> Unit = {},
    onRootFilterBadgeClick: () -> Unit = {},
) {
    val watcherSummary = stringResource(R.string.corpsefinder_watcher_summary)

    val rootGate = SettingGate.SetupRequired

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.corpsefinder_tool_name)) },
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
            item { SettingsCategoryHeader(text = stringResource(R.string.corpsefinder_watcher_title)) }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_details_24),
                    title = stringResource(R.string.corpsefinder_watcher_title),
                    subtitle = watcherSummary,
                    // Matches legacy: unchecked while not pro regardless of stored value.
                    checked = state.isPro && state.isWatcherEnabled,
                    onCheckedChange = onWatcherChanged,
                    requiresUpgrade = !state.isPro,
                    onUpgrade = onWatcherBadgeClick,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_delete_alert_24),
                    title = stringResource(R.string.corpsefinder_watcher_autodelete_title),
                    subtitle = stringResource(R.string.corpsefinder_watcher_autodelete_summary),
                    checked = state.isWatcherAutoDeleteEnabled,
                    onCheckedChange = onWatcherAutoDeleteChanged,
                    // Disabled unless the watcher itself is on (pro-aware view of the flag).
                    enabled = state.isPro && state.isWatcherEnabled,
                )
            }

            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_risklevel)) }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.PhotoLibrary,
                    title = stringResource(R.string.corpsefinder_settings_risk_keeper_title),
                    subtitle = stringResource(R.string.corpsefinder_settings_risk_keeper_summary),
                    checked = state.includeRiskKeeper,
                    onCheckedChange = onIncludeRiskKeeperChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_baseline_warning_24),
                    title = stringResource(R.string.corpsefinder_settings_risk_common_title),
                    subtitle = stringResource(R.string.corpsefinder_settings_risk_common_summary),
                    checked = state.includeRiskCommon,
                    onCheckedChange = onIncludeRiskCommonChanged,
                )
            }

            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_filter)) }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_sd_storage),
                    title = stringResource(R.string.corpsefinder_filter_sdcard_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_sdcard_summary),
                    checked = state.filterSdcardEnabled,
                    onCheckedChange = onFilterSdcardChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_sd_storage),
                    title = stringResource(R.string.corpsefinder_filter_publicmedia_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_publicmedia_summary),
                    checked = state.filterPublicMediaEnabled,
                    onCheckedChange = onFilterPublicMediaChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_sd_storage),
                    title = stringResource(R.string.corpsefinder_filter_publicdata_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_publicdata_summary),
                    checked = state.filterPublicDataEnabled,
                    onCheckedChange = onFilterPublicDataChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_game_controller_24),
                    title = stringResource(R.string.corpsefinder_filter_publicobb_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_publicobb_summary),
                    checked = state.filterPublicObbEnabled,
                    onCheckedChange = onFilterPublicObbChanged,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_incognito_circle_24),
                    title = stringResource(R.string.corpsefinder_filter_privatedata_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_privatedata_summary),
                    checked = state.filterPrivateDataEnabled,
                    onCheckedChange = onFilterPrivateDataChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterPrivateDataAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_turbine_24),
                    title = stringResource(R.string.corpsefinder_filter_dalvik_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_dalvik_summary),
                    checked = state.filterDalvikCacheEnabled,
                    onCheckedChange = onFilterDalvikCacheChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterDalvikCacheAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_artboard_24),
                    title = stringResource(R.string.corpsefinder_filter_artprofiles_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_artprofiles_summary),
                    checked = state.filterArtProfilesEnabled,
                    onCheckedChange = onFilterArtProfilesChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterArtProfilesAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    icon = Icons.TwoTone.LocalLibrary,
                    title = stringResource(R.string.corpsefinder_filter_applib_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_applib_summary),
                    checked = state.filterAppLibEnabled,
                    onCheckedChange = onFilterAppLibChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterAppLibrariesAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_app_extra_24),
                    title = stringResource(R.string.corpsefinder_filter_appsource_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_appsource_summary),
                    checked = state.filterAppSourceEnabled,
                    onCheckedChange = onFilterAppSourceChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterAppSourcesAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_folder_key_24),
                    title = stringResource(R.string.corpsefinder_filter_appsource_private_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_appsource_private_summary),
                    checked = state.filterAppSourcePrivateEnabled,
                    onCheckedChange = onFilterAppSourcePrivateChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterPrivateAppSourcesAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_folder_key_24),
                    title = stringResource(R.string.corpsefinder_filter_appasec_label),
                    subtitle = stringResource(R.string.corpsefinder_filter_appasec_summary),
                    checked = state.filterAppSourceAsecEnabled,
                    onCheckedChange = onFilterAppSourceAsecChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.isFilterAppSourcesAvailable) null else rootGate,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun CorpseFinderSettingsScreenPreviewRooted() {
    PreviewWrapper {
        CorpseFinderSettingsScreen(
            state = CorpseFinderSettingsViewModel.State(
                isPro = true,
                isWatcherEnabled = true,
                isFilterPrivateDataAvailable = true,
                isFilterDalvikCacheAvailable = true,
                isFilterArtProfilesAvailable = true,
                isFilterAppLibrariesAvailable = true,
                isFilterAppSourcesAvailable = true,
                isFilterPrivateAppSourcesAvailable = true,
            ),
        )
    }
}

@Preview2
@Composable
private fun CorpseFinderSettingsScreenPreviewFree() {
    PreviewWrapper {
        CorpseFinderSettingsScreen(
            state = CorpseFinderSettingsViewModel.State(
                isPro = false,
                isWatcherEnabled = false,
            ),
        )
    }
}
