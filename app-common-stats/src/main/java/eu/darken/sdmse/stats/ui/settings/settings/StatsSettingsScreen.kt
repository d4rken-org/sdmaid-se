package eu.darken.sdmse.stats.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Assessment
import androidx.compose.material.icons.twotone.BarChart
import androidx.compose.material.icons.twotone.SettingsBackupRestore
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.formatAge
import eu.darken.sdmse.common.stats.R
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import eu.darken.sdmse.common.R as CommonR

@Composable
fun StatsSettingsScreenHost(
    vm: StatsSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    StatsSettingsScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onViewStatsClick = vm::onViewStatsClick,
        onRetentionReportsSaved = vm::setRetentionReports,
        onRetentionReportsReset = vm::resetRetentionReports,
        onRetentionPathsSaved = vm::setRetentionPaths,
        onRetentionPathsReset = vm::resetRetentionPaths,
        onResetAllConfirmed = vm::resetAll,
    )
}

@Composable
internal fun StatsSettingsScreen(
    stateSource: StateFlow<StatsSettingsViewModel.State> =
        MutableStateFlow(StatsSettingsViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onViewStatsClick: () -> Unit = {},
    onRetentionReportsSaved: (Duration) -> Unit = {},
    onRetentionReportsReset: () -> Unit = {},
    onRetentionPathsSaved: (Duration) -> Unit = {},
    onRetentionPathsReset: () -> Unit = {},
    onResetAllConfirmed: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    var showReportsAgeDialog by remember { mutableStateOf(false) }
    var showPathsAgeDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showReportsAgeDialog) {
        AgeInputDialog(
            titleRes = R.string.stats_settings_retention_reports_label,
            currentAge = state.retentionReports,
            maximumAge = Duration.ofDays(365),
            onSave = {
                onRetentionReportsSaved(it)
                showReportsAgeDialog = false
            },
            onReset = {
                onRetentionReportsReset()
                showReportsAgeDialog = false
            },
            onDismiss = { showReportsAgeDialog = false },
        )
    }

    if (showPathsAgeDialog) {
        AgeInputDialog(
            titleRes = R.string.stats_settings_retention_paths_label,
            currentAge = state.retentionPaths,
            maximumAge = Duration.ofDays(365),
            onSave = {
                onRetentionPathsSaved(it)
                showPathsAgeDialog = false
            },
            onReset = {
                onRetentionPathsReset()
                showPathsAgeDialog = false
            },
            onDismiss = { showPathsAgeDialog = false },
        )
    }

    if (showResetAllDialog) {
        SdmConfirmDialog(
            title = stringResource(R.string.stats_settings_reset_all_label),
            message = stringResource(R.string.stats_settings_reset_all_desc),
            onDismissRequest = { showResetAllDialog = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_reset_action),
                onClick = {
                    onResetAllConfirmed()
                    showResetAllDialog = false
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { showResetAllDialog = false },
            ),
        )
    }

    val statsSummary = run {
        val (space, spaceQuantity) = ByteFormatter.formatSize(context, state.totalSpaceFreed)
        val spaceFormatted = pluralStringResource(
            R.plurals.stats_dash_body_size,
            spaceQuantity,
            space,
        )
        val processed = state.itemsProcessed.toString()
        val processedFormatted = pluralStringResource(
            R.plurals.stats_dash_body_count,
            state.itemsProcessed.toInt(),
            processed,
        )
        "$spaceFormatted $processedFormatted"
    }

    val resetSummary = pluralStringResource(
        CommonR.plurals.result_x_items,
        state.reportsCount,
        state.reportsCount,
    )

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.stats_label)) },
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
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.BarChart,
                    title = stringResource(CommonR.string.stats_label),
                    subtitle = statsSummary,
                    onClick = onViewStatsClick,
                )
            }
            item { SettingsCategoryHeader(text = stringResource(R.string.stats_settings_retention_category)) }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.BarChart,
                    title = stringResource(R.string.stats_settings_retention_reports_label),
                    subtitle = stringResource(R.string.stats_settings_retention_reports_desc),
                    value = formatAge(context, state.retentionReports),
                    onClick = { showReportsAgeDialog = true },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Assessment,
                    title = stringResource(R.string.stats_settings_retention_paths_label),
                    subtitle = stringResource(R.string.stats_settings_retention_paths_desc),
                    value = formatAge(context, state.retentionPaths),
                    onClick = { showPathsAgeDialog = true },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.SettingsBackupRestore,
                    title = stringResource(R.string.stats_settings_reset_all_label),
                    subtitle = resetSummary,
                    onClick = { showResetAllDialog = true },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun StatsSettingsScreenPreview() {
    PreviewWrapper {
        StatsSettingsScreen(
            stateSource = MutableStateFlow(
                StatsSettingsViewModel.State(
                    reportsCount = 42,
                    totalSpaceFreed = 12 * 1024L * 1024L,
                    itemsProcessed = 2048L,
                ),
            ),
        )
    }
}
