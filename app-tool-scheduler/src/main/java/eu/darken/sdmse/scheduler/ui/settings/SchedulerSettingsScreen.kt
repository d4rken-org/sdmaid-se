package eu.darken.sdmse.scheduler.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AccessibilityNew
import androidx.compose.material.icons.twotone.BatteryAlert
import androidx.compose.material.icons.twotone.PowerOff
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
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.scheduler.R

@Composable
fun SchedulerSettingsScreenHost(
    vm: SchedulerSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    SchedulerSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onSkipPowerSavingChanged = vm::setSkipWhenPowerSaving,
        onSkipNotChargingChanged = vm::setSkipWhenNotCharging,
        onUseAutomationChanged = vm::setUseAutomation,
    )
}

@Composable
internal fun SchedulerSettingsScreen(
    state: SchedulerSettingsViewModel.State = SchedulerSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onSkipPowerSavingChanged: (Boolean) -> Unit = {},
    onSkipNotChargingChanged: (Boolean) -> Unit = {},
    onUseAutomationChanged: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduler_label)) },
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
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.BatteryAlert,
                    title = stringResource(R.string.scheduler_setting_nopowersaving_title),
                    subtitle = stringResource(R.string.scheduler_setting_nopowersaving_summary),
                    checked = state.skipWhenPowerSaving,
                    onCheckedChange = onSkipPowerSavingChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.PowerOff,
                    title = stringResource(R.string.scheduler_setting_charging_title),
                    subtitle = stringResource(R.string.scheduler_setting_charging_summary),
                    checked = state.skipWhenNotCharging,
                    onCheckedChange = onSkipNotChargingChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.AccessibilityNew,
                    title = stringResource(R.string.scheduler_setting_automation_title),
                    subtitle = stringResource(R.string.scheduler_setting_automation_summary),
                    checked = state.useAutomation,
                    onCheckedChange = onUseAutomationChanged,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SchedulerSettingsScreenPreview() {
    PreviewWrapper {
        SchedulerSettingsScreen(
            state = SchedulerSettingsViewModel.State(
                skipWhenPowerSaving = true,
                skipWhenNotCharging = false,
                useAutomation = false,
            ),
        )
    }
}
