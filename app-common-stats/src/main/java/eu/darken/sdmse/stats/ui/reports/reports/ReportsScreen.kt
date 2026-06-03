package eu.darken.sdmse.stats.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.StackedBarChart
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.ui.reports.items.ReportRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.UUID

@Composable
fun ReportsScreenHost(
    vm: ReportsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    var reportError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ReportsViewModel.Event.ShowError -> reportError = event.msg
            }
        }
    }

    ReportsScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onStorageTrendClick = vm::onStorageTrendClick,
        onReportClick = vm::onReportClick,
    )

    reportError?.let { msg ->
        SdmConfirmDialog(
            title = stringResource(CommonR.string.general_error_label),
            message = msg,
            onDismissRequest = { reportError = null },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_dismiss_action),
                onClick = { reportError = null },
            ),
        )
    }
}

@Composable
internal fun ReportsScreen(
    stateSource: StateFlow<ReportsViewModel.State> = MutableStateFlow(ReportsViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onStorageTrendClick: () -> Unit = {},
    onReportClick: (ReportsViewModel.Row) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    val subtitle = state.rows?.size?.let { count ->
        pluralStringResource(CommonR.plurals.result_x_items, count, count)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(CommonR.string.stats_label))
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
                actions = {
                    SdmTooltipIconButton(
                        icon = if (state.isPro) {
                            Icons.TwoTone.StackedBarChart
                        } else {
                            Icons.TwoTone.Stars
                        },
                        label = stringResource(R.string.stats_storage_trend_action),
                        onClick = onStorageTrendClick,
                    )
                },
            )
        },
    ) { paddingValues ->
        val rows = state.rows
        when {
            rows == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            rows.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(CommonR.string.general_empty_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                items(rows, key = { it.reportId }) { row ->
                    ReportRow(
                        row = row,
                        now = state.now,
                        onClick = { onReportClick(row) },
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun ReportsScreenLoadingPreview() {
    PreviewWrapper {
        ReportsScreen(
            stateSource = MutableStateFlow(ReportsViewModel.State(rows = null)),
        )
    }
}

@Preview2
@Composable
private fun ReportsScreenEmptyPreview() {
    PreviewWrapper {
        ReportsScreen(
            stateSource = MutableStateFlow(ReportsViewModel.State(rows = emptyList())),
        )
    }
}

@Preview2
@Composable
private fun ReportsScreenPopulatedPreview() {
    val now = Instant.now()
    PreviewWrapper {
        ReportsScreen(
            stateSource = MutableStateFlow(
                ReportsViewModel.State(
                    rows = listOf(
                        ReportsViewModel.Row(
                            reportId = UUID.randomUUID(),
                            tool = SDMTool.Type.CORPSEFINDER,
                            status = Report.Status.SUCCESS,
                            endAt = now.minusSeconds(120),
                            primaryMessage = "Freed 12 MB",
                            secondaryMessage = "Removed 42 items",
                            errorMessage = null,
                        ),
                        ReportsViewModel.Row(
                            reportId = UUID.randomUUID(),
                            tool = SDMTool.Type.APPCLEANER,
                            status = Report.Status.PARTIAL_SUCCESS,
                            endAt = now.minusSeconds(3600),
                            primaryMessage = "Freed 4 MB",
                            secondaryMessage = null,
                            errorMessage = null,
                        ),
                        ReportsViewModel.Row(
                            reportId = UUID.randomUUID(),
                            tool = SDMTool.Type.DEDUPLICATOR,
                            status = Report.Status.FAILURE,
                            endAt = now.minusSeconds(86400),
                            primaryMessage = null,
                            secondaryMessage = null,
                            errorMessage = "Storage access revoked mid-operation",
                        ),
                    ),
                    isPro = true,
                    now = now,
                ),
            ),
        )
    }
}
