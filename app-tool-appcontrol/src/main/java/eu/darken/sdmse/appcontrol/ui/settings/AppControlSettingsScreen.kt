package eu.darken.sdmse.appcontrol.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingGate
import eu.darken.sdmse.common.compose.settings.SettingsBadgedSwitchItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.R as CommonR

@Composable
fun AppControlSettingsScreenHost(
    vm: AppControlSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    AppControlSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onSizingChanged = vm::setSizingEnabled,
        onSizingBadgeClick = vm::onSizingBadgeClick,
        onActivityChanged = vm::setActivityEnabled,
        onActivityBadgeClick = vm::onActivityBadgeClick,
        onMultiUserChanged = vm::setMultiUserEnabled,
        onMultiUserBadgeClick = vm::onMultiUserBadgeClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppControlSettingsScreen(
    state: AppControlSettingsViewModel.State = AppControlSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onSizingChanged: (Boolean) -> Unit = {},
    onSizingBadgeClick: () -> Unit = {},
    onActivityChanged: (Boolean) -> Unit = {},
    onActivityBadgeClick: () -> Unit = {},
    onMultiUserChanged: (Boolean) -> Unit = {},
    onMultiUserBadgeClick: () -> Unit = {},
) {
    val multiUserSummary = stringResource(CommonR.string.general_include_multiuser_summary)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.appcontrol_tool_name)) },
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
                SettingsBadgedSwitchItem(
                    icon = Icons.Outlined.MonitorWeight,
                    title = stringResource(R.string.appcontrol_settings_module_sizing_enabled_label),
                    subtitle = stringResource(R.string.appcontrol_settings_module_sizing_enabled_description),
                    checked = state.sizingEnabled,
                    onCheckedChange = onSizingChanged,
                    onBadgeClick = onSizingBadgeClick,
                    gate = if (state.canInfoSize) null else SettingGate.SetupRequired,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    icon = Icons.Outlined.DirectionsRun,
                    title = stringResource(R.string.appcontrol_settings_module_activity_enabled_label),
                    subtitle = stringResource(R.string.appcontrol_settings_module_activity_enabled_description),
                    checked = state.activityEnabled,
                    onCheckedChange = onActivityChanged,
                    onBadgeClick = onActivityBadgeClick,
                    gate = if (state.canInfoActive) null else SettingGate.SetupRequired,
                )
            }
            item {
                SettingsBadgedSwitchItem(
                    icon = Icons.Outlined.Groups,
                    title = stringResource(CommonR.string.general_include_multiuser_label),
                    subtitle = multiUserSummary,
                    // Non-Pro users see an unchecked switch regardless of the stored value.
                    checked = state.isPro && state.multiUserEnabled,
                    onCheckedChange = onMultiUserChanged,
                    onBadgeClick = onMultiUserBadgeClick,
                    gate = if (state.isPro && !state.canIncludeMultiUser) SettingGate.SetupRequired else null,
                    requiresUpgrade = !state.isPro,
                    onUpgrade = onMultiUserBadgeClick,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AppControlSettingsScreenPreviewReady() {
    PreviewWrapper {
        AppControlSettingsScreen(
            state = AppControlSettingsViewModel.State(
                isPro = true,
                sizingEnabled = true,
                activityEnabled = true,
                multiUserEnabled = false,
                canInfoSize = true,
                canInfoActive = true,
                canIncludeMultiUser = true,
            ),
        )
    }
}

@Preview2
@Composable
private fun AppControlSettingsScreenPreviewFree() {
    PreviewWrapper {
        AppControlSettingsScreen(
            state = AppControlSettingsViewModel.State(
                isPro = false,
                sizingEnabled = true,
                activityEnabled = true,
                multiUserEnabled = false,
                canInfoSize = false,
                canInfoActive = false,
                canIncludeMultiUser = false,
            ),
        )
    }
}
