package eu.darken.sdmse.appcontrol.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.DirectionsRun
import androidx.compose.material.icons.twotone.Groups
import androidx.compose.material.icons.twotone.MonitorWeight
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.FeatureGateState
import eu.darken.sdmse.common.compose.settings.SettingGate
import eu.darken.sdmse.common.compose.settings.SettingsBadgedSwitchItem
import eu.darken.sdmse.common.compose.settings.rememberGateClickHandler
import eu.darken.sdmse.common.compose.settings.toSettingGate
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
    val snackbarHostState = remember { SnackbarHostState() }

    AppControlSettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onSizingChanged = vm::setSizingEnabled,
        onSizingBadgeClick = vm::onSizingBadgeClick,
        onActivityChanged = vm::setActivityEnabled,
        onActivityBadgeClick = vm::onActivityBadgeClick,
        onMultiUserChanged = vm::setMultiUserEnabled,
        onMultiUserBadgeClick = vm::onMultiUserBadgeClick,
    )
}

@Composable
internal fun AppControlSettingsScreen(
    state: AppControlSettingsViewModel.State = AppControlSettingsViewModel.State(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onNavigateUp: () -> Unit = {},
    onSizingChanged: (Boolean) -> Unit = {},
    onSizingBadgeClick: () -> Unit = {},
    onActivityChanged: (Boolean) -> Unit = {},
    onActivityBadgeClick: () -> Unit = {},
    onMultiUserChanged: (Boolean) -> Unit = {},
    onMultiUserBadgeClick: () -> Unit = {},
) {
    val multiUserSummary = stringResource(CommonR.string.general_include_multiuser_summary)

    val gateClick = rememberGateClickHandler(snackbarHostState)
    val multiUserBlockedMessage = stringResource(CommonR.string.access_blocked_root_or_shizuku)

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.appcontrol_tool_name)) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            item {
                SettingsBadgedSwitchItem(
                    icon = Icons.TwoTone.MonitorWeight,
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
                    icon = Icons.AutoMirrored.TwoTone.DirectionsRun,
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
                    icon = Icons.TwoTone.Groups,
                    title = stringResource(CommonR.string.general_include_multiuser_label),
                    subtitle = multiUserSummary,
                    // Non-Pro users see an unchecked switch regardless of the stored value.
                    checked = state.isPro && state.multiUserEnabled,
                    onCheckedChange = onMultiUserChanged,
                    onBadgeClick = {
                        gateClick(state.multiUserGate, multiUserBlockedMessage, onMultiUserBadgeClick)
                    },
                    gate = state.multiUserGate.toSettingGate(),
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
                multiUserGate = FeatureGateState.AVAILABLE,
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
