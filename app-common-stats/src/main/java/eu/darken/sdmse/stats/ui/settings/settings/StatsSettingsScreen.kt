package eu.darken.sdmse.stats.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.formatAge
import eu.darken.sdmse.common.stats.R
import java.time.Duration
import eu.darken.sdmse.common.R as CommonR

@Composable
fun StatsSettingsScreenHost(
    vm: StatsSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    StatsSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onViewStatsClick = vm::onViewStatsClick,
        onRetentionReportsSaved = vm::setRetentionReports,
        onRetentionReportsReset = vm::resetRetentionReports,
        onRetentionPathsSaved = vm::setRetentionPaths,
        onRetentionPathsReset = vm::resetRetentionPaths,
        onResetAllConfirmed = vm::resetAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsSettingsScreen(
    state: StatsSettingsViewModel.State = StatsSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onViewStatsClick: () -> Unit = {},
    onRetentionReportsSaved: (Duration) -> Unit = {},
    onRetentionReportsReset: () -> Unit = {},
    onRetentionPathsSaved: (Duration) -> Unit = {},
    onRetentionPathsReset: () -> Unit = {},
    onResetAllConfirmed: () -> Unit = {},
) {
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
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text(stringResource(R.string.stats_settings_reset_all_label)) },
            text = { Text(stringResource(R.string.stats_settings_reset_all_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    onResetAllConfirmed()
                    showResetAllDialog = false
                }) { Text(stringResource(CommonR.string.general_reset_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.stats_label)) },
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
                SettingsPreferenceItem(
                    icon = Icons.Outlined.BarChart,
                    title = stringResource(CommonR.string.stats_label),
                    subtitle = statsSummary,
                    onClick = onViewStatsClick,
                )
            }
            item { SettingsCategoryHeader(text = stringResource(R.string.stats_settings_retention_category)) }
            item {
                SettingsPreferenceItem(
                    icon = Icons.Outlined.BarChart,
                    title = stringResource(R.string.stats_settings_retention_reports_label),
                    subtitle = stringResource(R.string.stats_settings_retention_reports_desc),
                    value = formatAge(context, state.retentionReports),
                    onClick = { showReportsAgeDialog = true },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.Outlined.Assessment,
                    title = stringResource(R.string.stats_settings_retention_paths_label),
                    subtitle = stringResource(R.string.stats_settings_retention_paths_desc),
                    value = formatAge(context, state.retentionPaths),
                    onClick = { showPathsAgeDialog = true },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.Outlined.SettingsBackupRestore,
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
            state = StatsSettingsViewModel.State(
                reportsCount = 42,
                totalSpaceFreed = 12 * 1024L * 1024L,
                itemsProcessed = 2048L,
            ),
        )
    }
}
