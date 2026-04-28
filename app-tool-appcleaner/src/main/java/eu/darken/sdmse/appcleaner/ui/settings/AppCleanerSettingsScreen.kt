package eu.darken.sdmse.appcleaner.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.SignalCellularOff
import androidx.compose.material.icons.outlined.VisibilityOff
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
import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.common.compose.icons.Chrome
import eu.darken.sdmse.common.compose.icons.Qq
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.WeChat
import eu.darken.sdmse.common.compose.icons.WhatsApp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingGate
import eu.darken.sdmse.common.compose.settings.SettingsBadgedSwitchItem
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.formatAge
import java.time.Duration
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ui.R as UiR

@Composable
fun AppCleanerSettingsScreenHost(
    vm: AppCleanerSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    AppCleanerSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onIncludeSystemAppsChanged = vm::setIncludeSystemApps,
        onIncludeOtherUsersChanged = vm::setIncludeOtherUsers,
        onIncludeOtherUsersBadge = vm::onOtherUsersBadgeClick,
        onIncludeRunningAppsChanged = vm::setIncludeRunningApps,
        onIncludeRunningAppsBadge = vm::onRunningAppsBadgeClick,
        onIncludeInaccessibleChanged = vm::setIncludeInaccessible,
        onIncludeInaccessibleBadge = vm::onInaccessibleBadgeClick,
        onForceStopChanged = vm::setForceStopBeforeClearing,
        onForceStopBadge = vm::onForceStopBadgeClick,
        onMinCacheSizeChanged = vm::setMinCacheSizeBytes,
        onMinCacheSizeReset = vm::resetMinCacheSizeBytes,
        onMinCacheAgeChanged = vm::setMinCacheAgeMs,
        onMinCacheAgeReset = vm::resetMinCacheAgeMs,
        onFilterDefaultCachesPublicChanged = vm::setFilterDefaultCachesPublic,
        onFilterDefaultCachesPrivateChanged = vm::setFilterDefaultCachesPrivate,
        onFilterHiddenCachesChanged = vm::setFilterHiddenCaches,
        onFilterThumbnailsChanged = vm::setFilterThumbnails,
        onFilterCodeCacheChanged = vm::setFilterCodeCache,
        onFilterAdvertisementChanged = vm::setFilterAdvertisement,
        onFilterBugreportingChanged = vm::setFilterBugreporting,
        onFilterAnalyticsChanged = vm::setFilterAnalytics,
        onFilterGameFilesChanged = vm::setFilterGameFiles,
        onFilterOfflineCacheChanged = vm::setFilterOfflineCache,
        onFilterRecycleBinsChanged = vm::setFilterRecycleBins,
        onFilterWebviewChanged = vm::setFilterWebview,
        onFilterShortcutServiceChanged = vm::setFilterShortcutService,
        onFilterWhatsappBackupsChanged = vm::setFilterWhatsappBackups,
        onFilterWhatsappReceivedChanged = vm::setFilterWhatsappReceived,
        onFilterWhatsappSentChanged = vm::setFilterWhatsappSent,
        onFilterTelegramChanged = vm::setFilterTelegram,
        onFilterThreemaChanged = vm::setFilterThreema,
        onFilterWeChatChanged = vm::setFilterWeChat,
        onFilterViberChanged = vm::setFilterViber,
        onFilterMobileQQChanged = vm::setFilterMobileQQ,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppCleanerSettingsScreen(
    state: AppCleanerSettingsViewModel.State = AppCleanerSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onIncludeSystemAppsChanged: (Boolean) -> Unit = {},
    onIncludeOtherUsersChanged: (Boolean) -> Unit = {},
    onIncludeOtherUsersBadge: () -> Unit = {},
    onIncludeRunningAppsChanged: (Boolean) -> Unit = {},
    onIncludeRunningAppsBadge: () -> Unit = {},
    onIncludeInaccessibleChanged: (Boolean) -> Unit = {},
    onIncludeInaccessibleBadge: () -> Unit = {},
    onForceStopChanged: (Boolean) -> Unit = {},
    onForceStopBadge: () -> Unit = {},
    onMinCacheSizeChanged: (Long) -> Unit = {},
    onMinCacheSizeReset: () -> Unit = {},
    onMinCacheAgeChanged: (Long) -> Unit = {},
    onMinCacheAgeReset: () -> Unit = {},
    onFilterDefaultCachesPublicChanged: (Boolean) -> Unit = {},
    onFilterDefaultCachesPrivateChanged: (Boolean) -> Unit = {},
    onFilterHiddenCachesChanged: (Boolean) -> Unit = {},
    onFilterThumbnailsChanged: (Boolean) -> Unit = {},
    onFilterCodeCacheChanged: (Boolean) -> Unit = {},
    onFilterAdvertisementChanged: (Boolean) -> Unit = {},
    onFilterBugreportingChanged: (Boolean) -> Unit = {},
    onFilterAnalyticsChanged: (Boolean) -> Unit = {},
    onFilterGameFilesChanged: (Boolean) -> Unit = {},
    onFilterOfflineCacheChanged: (Boolean) -> Unit = {},
    onFilterRecycleBinsChanged: (Boolean) -> Unit = {},
    onFilterWebviewChanged: (Boolean) -> Unit = {},
    onFilterShortcutServiceChanged: (Boolean) -> Unit = {},
    onFilterWhatsappBackupsChanged: (Boolean) -> Unit = {},
    onFilterWhatsappReceivedChanged: (Boolean) -> Unit = {},
    onFilterWhatsappSentChanged: (Boolean) -> Unit = {},
    onFilterTelegramChanged: (Boolean) -> Unit = {},
    onFilterThreemaChanged: (Boolean) -> Unit = {},
    onFilterWeChatChanged: (Boolean) -> Unit = {},
    onFilterViberChanged: (Boolean) -> Unit = {},
    onFilterMobileQQChanged: (Boolean) -> Unit = {},
) {
    var showSizeDialog by remember { mutableStateOf(false) }
    var showAgeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showSizeDialog) {
        SizeInputDialog(
            titleRes = R.string.appcleaner_include_minimumsize_label,
            currentSize = state.minCacheSizeBytes,
            onSave = { size ->
                onMinCacheSizeChanged(size)
                showSizeDialog = false
            },
            onReset = {
                onMinCacheSizeReset()
                showSizeDialog = false
            },
            onDismiss = { showSizeDialog = false },
        )
    }
    if (showAgeDialog) {
        AgeInputDialog(
            titleRes = R.string.appcleaner_include_minimumage_label,
            currentAge = Duration.ofMillis(state.minCacheAgeMs),
            maximumAge = Duration.ofDays(182),
            onSave = { duration ->
                onMinCacheAgeChanged(duration.toMillis())
                showAgeDialog = false
            },
            onReset = {
                onMinCacheAgeReset()
                showAgeDialog = false
            },
            onDismiss = { showAgeDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.appcleaner_tool_name)) },
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
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.appcleaner_include_systemapps_label),
                    subtitle = stringResource(R.string.appcleaner_include_systemapps_summary),
                    checked = state.includeSystemApps,
                    onCheckedChange = onIncludeSystemAppsChanged,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    icon = Icons.Outlined.Groups,
                    title = stringResource(CommonR.string.general_include_multiuser_label),
                    subtitle = stringResource(CommonR.string.general_include_multiuser_summary),
                    checked = state.includeOtherUsers,
                    onCheckedChange = onIncludeOtherUsersChanged,
                    onBadgeClick = onIncludeOtherUsersBadge,
                    gate = if (state.isOtherUsersAvailable) null else SettingGate.SetupRequired,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    icon = Icons.Outlined.RotateRight,
                    title = stringResource(R.string.appcleaner_include_runningapps_label),
                    subtitle = stringResource(R.string.appcleaner_include_runningapps_summary),
                    checked = state.includeRunningApps,
                    onCheckedChange = onIncludeRunningAppsChanged,
                    onBadgeClick = onIncludeRunningAppsBadge,
                    gate = if (state.isRunningAppsDetectionAvailable) null else SettingGate.SetupRequired,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                    title = stringResource(R.string.appcleaner_include_minimumsize_label),
                    subtitle = stringResource(R.string.appcleaner_include_minimumsize_summary),
                    value = Formatter.formatShortFileSize(context, state.minCacheSizeBytes),
                    onClick = { showSizeDialog = true },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.appcleaner_include_minimumage_label),
                    subtitle = stringResource(R.string.appcleaner_include_minimumage_summary),
                    value = formatAge(context, Duration.ofMillis(state.minCacheAgeMs)),
                    onClick = { showAgeDialog = true },
                )
            }
            if (state.isAcsRequired) {
                item {
                    SettingsBadgedSwitchItem(
                        icon = Icons.Outlined.FolderOff,
                        title = stringResource(R.string.appcleaner_include_inaccessible_label),
                        subtitle = stringResource(R.string.appcleaner_include_inaccessible_summary),
                        checked = state.includeInaccessible,
                        onCheckedChange = onIncludeInaccessibleChanged,
                        onBadgeClick = onIncludeInaccessibleBadge,
                        gate = if (state.isInaccessibleCacheAvailable) null else SettingGate.SetupRequired,
                    )
                }
                item {
                    SettingsBadgedSwitchItem(
                        icon = Icons.Outlined.Cancel,
                        title = stringResource(R.string.appcleaner_forcestop_before_clearing_label),
                        subtitle = stringResource(R.string.appcleaner_forcestop_before_clearing_summary),
                        checked = state.forceStopBeforeClearing,
                        onCheckedChange = onForceStopChanged,
                        onBadgeClick = onForceStopBadge,
                        gate = if (state.isInaccessibleCacheAvailable) null else SettingGate.SetupRequired,
                    )
                }
            }

            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_filter_generic)) }
            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    title = stringResource(R.string.appcleaner_filter_defaultcachespublic_label),
                    subtitle = stringResource(R.string.appcleaner_filter_defaultcachespublic_summary),
                    checked = state.filterDefaultCachesPublic,
                    onCheckedChange = onFilterDefaultCachesPublicChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    title = stringResource(R.string.appcleaner_filter_defaultcachesprivate_label),
                    subtitle = stringResource(R.string.appcleaner_filter_defaultcachesprivate_summary),
                    checked = state.filterDefaultCachesPrivate,
                    onCheckedChange = onFilterDefaultCachesPrivateChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.appcleaner_filter_hiddencaches_label),
                    subtitle = stringResource(R.string.appcleaner_filter_hiddencaches_summary),
                    checked = state.filterHiddenCaches,
                    onCheckedChange = onFilterHiddenCachesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.PlayCircleOutline,
                    title = stringResource(R.string.appcleaner_filter_thumbnails_label),
                    subtitle = stringResource(R.string.appcleaner_filter_thumbnails_summary),
                    checked = state.filterThumbnails,
                    onCheckedChange = onFilterThumbnailsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    title = stringResource(R.string.appcleaner_filter_codecache_label),
                    subtitle = stringResource(R.string.appcleaner_filter_codecache_summary),
                    checked = state.filterCodeCache,
                    onCheckedChange = onFilterCodeCacheChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.AdsClick,
                    title = stringResource(R.string.appcleaner_filter_advertisement_label),
                    subtitle = stringResource(R.string.appcleaner_filter_advertisement_summary),
                    checked = state.filterAdvertisement,
                    onCheckedChange = onFilterAdvertisementChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.appcleaner_filter_bugreporting_label),
                    subtitle = stringResource(R.string.appcleaner_filter_bugreporting_summary),
                    checked = state.filterBugreporting,
                    onCheckedChange = onFilterBugreportingChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Analytics,
                    title = stringResource(R.string.appcleaner_filter_analytics_label),
                    subtitle = stringResource(R.string.appcleaner_filter_analytics_summary),
                    checked = state.filterAnalytics,
                    onCheckedChange = onFilterAnalyticsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    iconPainter = painterResource(UiR.drawable.ic_game_controller_24),
                    title = stringResource(R.string.appcleaner_filter_gamefiles_label),
                    subtitle = stringResource(R.string.appcleaner_filter_gamefiles_summary),
                    checked = state.filterGameFiles,
                    onCheckedChange = onFilterGameFilesChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.SignalCellularOff,
                    title = stringResource(R.string.appcleaner_filter_offlinecache_label),
                    subtitle = stringResource(R.string.appcleaner_filter_offlinecache_summary),
                    checked = state.filterOfflineCache,
                    onCheckedChange = onFilterOfflineCacheChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DeleteForever,
                    title = stringResource(R.string.appcleaner_filter_recyclebins_label),
                    subtitle = stringResource(R.string.appcleaner_filter_recyclebins_summary),
                    checked = state.filterRecycleBins,
                    onCheckedChange = onFilterRecycleBinsChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.Chrome,
                    title = stringResource(R.string.appcleaner_filter_webview_label),
                    subtitle = stringResource(R.string.appcleaner_filter_webview_summary),
                    checked = state.filterWebview,
                    onCheckedChange = onFilterWebviewChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.appcleaner_filter_shortcutservice_label),
                    subtitle = stringResource(R.string.appcleaner_filter_shortcutservice_summary),
                    checked = state.filterShortcutService,
                    onCheckedChange = onFilterShortcutServiceChanged,
                )
            }

            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_filter_specific)) }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.WhatsApp,
                    title = stringResource(R.string.appcleaner_filter_whatsapp_backups_label),
                    subtitle = stringResource(R.string.appcleaner_filter_whatsapp_backups_summary),
                    checked = state.filterWhatsappBackups,
                    onCheckedChange = onFilterWhatsappBackupsChanged,
                )
            }
            item {
                // Legacy XML intentionally swaps label/summary for this row — preserve for parity.
                SettingsSwitchItem(
                    icon = SdmIcons.WhatsApp,
                    title = stringResource(R.string.appcleaner_filter_whatsapp_received_summary),
                    subtitle = stringResource(R.string.appcleaner_filter_whatsapp_received_label),
                    checked = state.filterWhatsappReceived,
                    onCheckedChange = onFilterWhatsappReceivedChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.WhatsApp,
                    title = stringResource(R.string.appcleaner_filter_whatsapp_sent_label),
                    subtitle = stringResource(R.string.appcleaner_filter_whatsapp_sent_summary),
                    checked = state.filterWhatsappSent,
                    onCheckedChange = onFilterWhatsappSentChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Chat,
                    title = stringResource(R.string.appcleaner_filter_telegram_label),
                    subtitle = stringResource(R.string.appcleaner_filter_telegram_summary),
                    checked = state.filterTelegram,
                    onCheckedChange = onFilterTelegramChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Chat,
                    title = stringResource(R.string.appcleaner_filter_threema_label),
                    subtitle = stringResource(R.string.appcleaner_filter_threema_summary),
                    checked = state.filterThreema,
                    onCheckedChange = onFilterThreemaChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.WeChat,
                    title = stringResource(R.string.appcleaner_filter_wechat_label),
                    subtitle = stringResource(R.string.appcleaner_filter_wechat_summary),
                    checked = state.filterWeChat,
                    onCheckedChange = onFilterWeChatChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Chat,
                    title = stringResource(R.string.appcleaner_filter_viber_label),
                    subtitle = stringResource(R.string.appcleaner_filter_viber_summary),
                    checked = state.filterViber,
                    onCheckedChange = onFilterViberChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.Qq,
                    title = stringResource(R.string.appcleaner_filter_qqchat_label),
                    subtitle = stringResource(R.string.appcleaner_filter_qqchat_summary),
                    checked = state.filterMobileQQ,
                    onCheckedChange = onFilterMobileQQChanged,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AppCleanerSettingsScreenPreviewAcsRequired() {
    PreviewWrapper {
        AppCleanerSettingsScreen(
            state = AppCleanerSettingsViewModel.State(
                isAcsRequired = true,
                isOtherUsersAvailable = true,
                isRunningAppsDetectionAvailable = true,
                isInaccessibleCacheAvailable = false,
            ),
        )
    }
}

@Preview2
@Composable
private fun AppCleanerSettingsScreenPreviewAcsNotRequired() {
    PreviewWrapper {
        AppCleanerSettingsScreen(
            state = AppCleanerSettingsViewModel.State(
                isAcsRequired = false,
                isOtherUsersAvailable = true,
                isRunningAppsDetectionAvailable = true,
                isInaccessibleCacheAvailable = true,
            ),
        )
    }
}
