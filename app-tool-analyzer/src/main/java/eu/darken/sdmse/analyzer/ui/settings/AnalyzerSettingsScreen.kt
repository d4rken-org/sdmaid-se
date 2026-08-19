package eu.darken.sdmse.analyzer.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.NotificationImportant
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.stats.core.LowStorage
import eu.darken.sdmse.common.R as CommonR

@Composable
fun AnalyzerSettingsScreenHost(
    vm: AnalyzerSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    AnalyzerSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onThresholdChanged = vm::setThreshold,
        onNotificationChanged = vm::setNotificationEnabled,
        onUpgradeClick = vm::onUpgradeClick,
    )
}

@Composable
internal fun AnalyzerSettingsScreen(
    state: AnalyzerSettingsViewModel.State = AnalyzerSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onThresholdChanged: (Long?) -> Unit = {},
    onNotificationChanged: (Boolean) -> Unit = {},
    onUpgradeClick: () -> Unit = {},
) {
    var showThresholdDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val thresholdValue = when {
        state.customThresholdBytes != null -> Formatter.formatShortFileSize(context, state.customThresholdBytes)
        state.effectiveThresholdBytes != null -> stringResource(
            R.string.analyzer_settings_lowstorage_value_auto,
            Formatter.formatShortFileSize(context, state.effectiveThresholdBytes),
        )
        else -> stringResource(R.string.analyzer_settings_lowstorage_value_auto_unknown)
    }

    if (showThresholdDialog) {
        SizeInputDialog(
            titleRes = R.string.analyzer_settings_lowstorage_title,
            currentSize = state.customThresholdBytes
                ?: state.effectiveThresholdBytes
                ?: LowStorage.AUTO_MAX_BYTES,
            minimumSize = AnalyzerSettings.LOW_STORAGE_THRESHOLD_MIN,
            // Deliberately a fixed ceiling, not one derived from this device's capacity: a
            // capacity-derived maximum would silently rewrite a restored custom value on Save,
            // and on a small device it could fall below the minimum and invert the range.
            maximumSize = AnalyzerSettings.LOW_STORAGE_THRESHOLD_MAX,
            resetLabelRes = R.string.analyzer_settings_lowstorage_automatic_action,
            onSave = {
                onThresholdChanged(it)
                showThresholdDialog = false
            },
            onReset = {
                onThresholdChanged(null)
                showThresholdDialog = false
            },
            onDismiss = { showThresholdDialog = false },
        )
    }

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.analyzer_tool_name)) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            item { SettingsCategoryHeader(text = stringResource(R.string.analyzer_settings_storage_category_label)) }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Storage,
                    title = stringResource(R.string.analyzer_settings_lowstorage_title),
                    subtitle = stringResource(R.string.analyzer_settings_lowstorage_desc),
                    value = thresholdValue,
                    onClick = { showThresholdDialog = true },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.NotificationImportant,
                    title = stringResource(R.string.analyzer_settings_lowspace_notification_title),
                    subtitle = stringResource(R.string.analyzer_settings_lowspace_notification_desc),
                    // Unchecked while not Pro regardless of the stored value.
                    checked = state.isPro && state.notificationEnabled,
                    onCheckedChange = onNotificationChanged,
                    requiresUpgrade = !state.isPro,
                    onUpgrade = onUpgradeClick,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AnalyzerSettingsScreenAutomaticPreview() {
    PreviewWrapper {
        AnalyzerSettingsScreen(
            state = AnalyzerSettingsViewModel.State(
                customThresholdBytes = null,
                primaryCapacityBytes = 128L * 1000 * 1000 * 1000,
                effectiveThresholdBytes = LowStorage.AUTO_MAX_BYTES,
            ),
        )
    }
}

@Preview2
@Composable
private fun AnalyzerSettingsScreenCustomPreview() {
    PreviewWrapper {
        AnalyzerSettingsScreen(
            state = AnalyzerSettingsViewModel.State(
                customThresholdBytes = 10L * 1000 * 1000 * 1000,
                primaryCapacityBytes = 128L * 1000 * 1000 * 1000,
                effectiveThresholdBytes = 10L * 1000 * 1000 * 1000,
            ),
        )
    }
}

@Preview2
@Composable
private fun AnalyzerSettingsScreenProPreview() {
    PreviewWrapper {
        AnalyzerSettingsScreen(
            state = AnalyzerSettingsViewModel.State(
                customThresholdBytes = null,
                primaryCapacityBytes = 128L * 1000 * 1000 * 1000,
                effectiveThresholdBytes = LowStorage.AUTO_MAX_BYTES,
                notificationEnabled = true,
                isPro = true,
            ),
        )
    }
}
