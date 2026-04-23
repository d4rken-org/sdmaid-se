package eu.darken.sdmse.systemcleaner.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingGate
import eu.darken.sdmse.common.compose.settings.SettingsBadgedSwitchItem
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.formatAge
import eu.darken.sdmse.systemcleaner.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ui.R as UiR

@Composable
fun SystemCleanerSettingsScreenHost(
    vm: SystemCleanerSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    SystemCleanerSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onCustomFiltersClick = vm::onCustomFiltersClick,
        onLogFilesChanged = vm::setFilterLogFiles,
        onAdvertisementsChanged = vm::setFilterAdvertisements,
        onEmptyDirectoriesChanged = vm::setFilterEmptyDirectories,
        onSuperfluosApksChanged = vm::setFilterSuperfluosApks,
        onSuperfluosApksIncludeSameVersionChanged = vm::setFilterSuperfluosApksIncludeSameVersion,
        onTrashedChanged = vm::setFilterTrashed,
        onScreenshotsChanged = vm::setFilterScreenshots,
        onScreenshotsAgeSaved = vm::setFilterScreenshotsAge,
        onScreenshotsAgeReset = vm::resetFilterScreenshotsAge,
        onLostDirChanged = vm::setFilterLostDir,
        onLinuxFilesChanged = vm::setFilterLinuxFiles,
        onMacFilesChanged = vm::setFilterMacFiles,
        onWindowsFilesChanged = vm::setFilterWindowsFiles,
        onTempFilesChanged = vm::setFilterTempFiles,
        onThumbnailsChanged = vm::setFilterThumbnails,
        onAnalyticsChanged = vm::setFilterAnalytics,
        onAnrChanged = vm::setFilterAnr,
        onLocalTmpChanged = vm::setFilterLocalTmp,
        onDownloadCacheChanged = vm::setFilterDownloadCache,
        onDataLoggerChanged = vm::setFilterDataLogger,
        onLogDropboxChanged = vm::setFilterLogDropbox,
        onRecentTasksChanged = vm::setFilterRecentTasks,
        onTombstonesChanged = vm::setFilterTombstones,
        onUsageStatsChanged = vm::setFilterUsageStats,
        onPackageCacheChanged = vm::setFilterPackageCache,
        onRootFilterBadgeClick = vm::onRootFilterBadgeClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemCleanerSettingsScreen(
    state: SystemCleanerSettingsViewModel.State = SystemCleanerSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onCustomFiltersClick: () -> Unit = {},
    onLogFilesChanged: (Boolean) -> Unit = {},
    onAdvertisementsChanged: (Boolean) -> Unit = {},
    onEmptyDirectoriesChanged: (Boolean) -> Unit = {},
    onSuperfluosApksChanged: (Boolean) -> Unit = {},
    onSuperfluosApksIncludeSameVersionChanged: (Boolean) -> Unit = {},
    onTrashedChanged: (Boolean) -> Unit = {},
    onScreenshotsChanged: (Boolean) -> Unit = {},
    onScreenshotsAgeSaved: (java.time.Duration) -> Unit = {},
    onScreenshotsAgeReset: () -> Unit = {},
    onLostDirChanged: (Boolean) -> Unit = {},
    onLinuxFilesChanged: (Boolean) -> Unit = {},
    onMacFilesChanged: (Boolean) -> Unit = {},
    onWindowsFilesChanged: (Boolean) -> Unit = {},
    onTempFilesChanged: (Boolean) -> Unit = {},
    onThumbnailsChanged: (Boolean) -> Unit = {},
    onAnalyticsChanged: (Boolean) -> Unit = {},
    onAnrChanged: (Boolean) -> Unit = {},
    onLocalTmpChanged: (Boolean) -> Unit = {},
    onDownloadCacheChanged: (Boolean) -> Unit = {},
    onDataLoggerChanged: (Boolean) -> Unit = {},
    onLogDropboxChanged: (Boolean) -> Unit = {},
    onRecentTasksChanged: (Boolean) -> Unit = {},
    onTombstonesChanged: (Boolean) -> Unit = {},
    onUsageStatsChanged: (Boolean) -> Unit = {},
    onPackageCacheChanged: (Boolean) -> Unit = {},
    onRootFilterBadgeClick: () -> Unit = {},
) {
    var showScreenshotAgeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showScreenshotAgeDialog) {
        AgeInputDialog(
            titleRes = R.string.systemcleaner_filter_screenshots_age_label,
            currentAge = state.screenshotsAge,
            onSave = {
                onScreenshotsAgeSaved(it)
                showScreenshotAgeDialog = false
            },
            onReset = {
                onScreenshotsAgeReset()
                showScreenshotAgeDialog = false
            },
            onDismiss = { showScreenshotAgeDialog = false },
        )
    }

    val rootGate = SettingGate.SetupRequired

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.systemcleaner_tool_name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            item { SettingsCategoryHeader(text = stringResource(R.string.systemcleaner_filter_custom_label)) }
            item {
                SettingsPreferenceItem(
                    iconPainter = painterResource(UiR.drawable.filter_multiple),
                    title = stringResource(R.string.systemcleaner_filter_custom_manage_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_custom_manage_summary),
                    onClick = onCustomFiltersClick,
                )
            }

            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_filter_generic)) }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_baseline_format_list_bulleted_24),
                    title = stringResource(R.string.systemcleaner_filter_logfiles_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_logfiles_summary),
                    checked = state.filterLogFiles,
                    onCheckedChange = onLogFilesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_baseline_ads_click_24),
                    title = stringResource(R.string.systemcleaner_filter_advertisements_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_advertisements_summary),
                    checked = state.filterAdvertisements,
                    onCheckedChange = onAdvertisementsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_baseline_folder_open_24),
                    title = stringResource(R.string.systemcleaner_filter_emptydirectories_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_emptydirectories_summary),
                    checked = state.filterEmptyDirectories,
                    onCheckedChange = onEmptyDirectoriesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_app_extra_24),
                    title = stringResource(R.string.systemcleaner_filter_superfluosapks_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_superfluosapks_summary),
                    checked = state.filterSuperfluosApks,
                    onCheckedChange = onSuperfluosApksChanged,
                )
            }
            if (state.filterSuperfluosApks) {
                item {
                    SettingsSwitchItem(
                        iconPainter = painterResource(UiR.drawable.ic_approximately_equal_24),
                        title = stringResource(R.string.systemcleaner_filter_superfluosapks_includesameversion_label),
                        subtitle = stringResource(R.string.systemcleaner_filter_superfluosapks_includesameversion_summary),
                        checked = state.filterSuperfluosApksIncludeSameVersion,
                        onCheckedChange = onSuperfluosApksIncludeSameVersionChanged,
                    )
                }
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_recycle_bin_24),
                    title = stringResource(R.string.systemcleaner_filter_trashed_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_trashed_summary),
                    checked = state.filterTrashed,
                    onCheckedChange = onTrashedChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_cellphone_screenshot_24),
                    title = stringResource(R.string.systemcleaner_filter_screenshots_label),
                    subtitle = stringResource(
                        R.string.systemcleaner_filter_screenshots_summary,
                        formatAge(context, state.screenshotsAge),
                    ),
                    checked = state.filterScreenshots,
                    onCheckedChange = onScreenshotsChanged,
                )
            }
            if (state.filterScreenshots) {
                item {
                    SettingsPreferenceItem(
                        iconPainter = painterResource(UiR.drawable.ic_file_clock_outline_24),
                        title = stringResource(R.string.systemcleaner_filter_screenshots_age_label),
                        subtitle = stringResource(R.string.systemcleaner_filter_screenshots_age_summary),
                        value = formatAge(context, state.screenshotsAge),
                        onClick = { showScreenshotAgeDialog = true },
                    )
                }
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_baseline_usb_24),
                    title = stringResource(R.string.systemcleaner_filter_lostdir_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_lostdir_summary),
                    checked = state.filterLostDir,
                    onCheckedChange = onLostDirChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_penguin_24),
                    title = stringResource(R.string.systemcleaner_filter_linuxfiles_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_linuxfiles_summary),
                    checked = state.filterLinuxFiles,
                    onCheckedChange = onLinuxFilesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_os_mac),
                    title = stringResource(R.string.systemcleaner_filter_macfiles_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_macfiles_summary),
                    checked = state.filterMacFiles,
                    onCheckedChange = onMacFilesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_os_windows),
                    title = stringResource(R.string.systemcleaner_filter_windowsfiles_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_windowsfiles_summary),
                    checked = state.filterWindowsFiles,
                    onCheckedChange = onWindowsFilesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_baseline_access_time_filled_24),
                    title = stringResource(R.string.systemcleaner_filter_tempfiles_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_tempfiles_summary),
                    checked = state.filterTempFiles,
                    onCheckedChange = onTempFilesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_thumb_up_24),
                    title = stringResource(R.string.systemcleaner_filter_thumbnails_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_thumbnails_summary),
                    checked = state.filterThumbnails,
                    onCheckedChange = onThumbnailsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_analytics_onsurface),
                    title = stringResource(R.string.systemcleaner_filter_analytics_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_analytics_summary),
                    checked = state.filterAnalytics,
                    onCheckedChange = onAnalyticsChanged,
                )
            }

            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_filter_specific)) }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_baseline_running_with_errors_24),
                    title = stringResource(R.string.systemcleaner_filter_anr_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_anr_summary),
                    checked = state.filterAnr,
                    onCheckedChange = onAnrChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_android_studio_24),
                    title = stringResource(R.string.systemcleaner_filter_localtmp_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_localtmp_summary),
                    checked = state.filterLocalTmp,
                    onCheckedChange = onLocalTmpChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_android_studio_24),
                    title = stringResource(R.string.systemcleaner_filter_downloadcache_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_downloadcache_summary),
                    checked = state.filterDownloadCache,
                    onCheckedChange = onDownloadCacheChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_baseline_format_list_bulleted_24),
                    title = stringResource(R.string.systemcleaner_filter_datalogger_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_datalogger_summary),
                    checked = state.filterDataLogger,
                    onCheckedChange = onDataLoggerChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_baseline_format_list_bulleted_24),
                    title = stringResource(R.string.systemcleaner_filter_logdropbox_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_logdropbox_summary),
                    checked = state.filterLogDropbox,
                    onCheckedChange = onLogDropboxChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_task_onsurface),
                    title = stringResource(R.string.systemcleaner_filter_recenttasks_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_recenttasks_summary),
                    checked = state.filterRecentTasks,
                    onCheckedChange = onRecentTasksChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_tombstone),
                    title = stringResource(R.string.systemcleaner_filter_tombstones_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_tombstones_summary),
                    checked = state.filterTombstones,
                    onCheckedChange = onTombstonesChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(R.drawable.ic_chart_bar_stacked_24),
                    title = stringResource(R.string.systemcleaner_filter_usagestats_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_usagestats_summary),
                    checked = state.filterUsageStats,
                    onCheckedChange = onUsageStatsChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    iconPainter = painterResource(CommonR.drawable.ic_apps),
                    title = stringResource(R.string.systemcleaner_filter_packagecaches_label),
                    subtitle = stringResource(R.string.systemcleaner_filter_packagecaches_summary),
                    checked = state.filterPackageCache,
                    onCheckedChange = onPackageCacheChanged,
                    onBadgeClick = onRootFilterBadgeClick,
                    gate = if (state.areSystemFilterAvailable) null else rootGate,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SystemCleanerSettingsScreenPreview() {
    PreviewWrapper {
        SystemCleanerSettingsScreen(
            state = SystemCleanerSettingsViewModel.State(
                isPro = true,
                areSystemFilterAvailable = true,
                filterScreenshots = true,
                filterSuperfluosApks = true,
            ),
        )
    }
}
