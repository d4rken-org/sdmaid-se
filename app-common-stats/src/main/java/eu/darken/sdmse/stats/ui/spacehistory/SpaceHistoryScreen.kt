package eu.darken.sdmse.stats.ui.spacehistory

import android.text.format.Formatter
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.stats.ui.SpaceHistoryRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.absoluteValue

@Composable
fun SpaceHistoryScreenHost(
    route: SpaceHistoryRoute,
    vm: SpaceHistoryViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(Unit) { vm.setInitialStorageId(route.storageId) }

    SpaceHistoryScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onSelectRange = vm::selectRange,
        onSelectStorage = vm::selectStorage,
        onDeleteStorage = vm::deleteStorage,
        onOpenUpgrade = vm::openUpgrade,
    )
}

@Composable
internal fun SpaceHistoryScreen(
    stateSource: StateFlow<SpaceHistoryViewModel.State> = MutableStateFlow(SpaceHistoryViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onSelectRange: (SpaceHistoryViewModel.Range) -> Unit = {},
    onSelectStorage: (String) -> Unit = {},
    onDeleteStorage: (String) -> Unit = {},
    onOpenUpgrade: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    var pendingDeleteStorageId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_space_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpaceHistoryChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                snapshots = state.snapshots,
                reports = state.reportMarkers,
            )

            Text(
                text = stringResource(R.string.stats_space_history_range_title),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeChip(
                    selected = state.selectedRange == SpaceHistoryViewModel.Range.DAYS_7,
                    enabled = true,
                    label = stringResource(R.string.stats_space_history_range_7d),
                    onClick = { onSelectRange(SpaceHistoryViewModel.Range.DAYS_7) },
                )
                RangeChip(
                    selected = state.selectedRange == SpaceHistoryViewModel.Range.DAYS_30,
                    enabled = state.isPro,
                    label = stringResource(R.string.stats_space_history_range_30d),
                    onClick = { onSelectRange(SpaceHistoryViewModel.Range.DAYS_30) },
                )
                RangeChip(
                    selected = state.selectedRange == SpaceHistoryViewModel.Range.DAYS_90,
                    enabled = state.isPro,
                    label = stringResource(R.string.stats_space_history_range_90d),
                    onClick = { onSelectRange(SpaceHistoryViewModel.Range.DAYS_90) },
                )
            }

            if (state.storages.size > 1) {
                Text(
                    text = stringResource(R.string.stats_space_history_storage_title),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (state.storages.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val context = LocalContext.current
                    state.storages.forEach { storage ->
                        StorageChip(
                            selected = storage.id == state.selectedStorageId,
                            label = storage.label.get(context),
                            onClick = { onSelectStorage(storage.id) },
                            onLongClick = { pendingDeleteStorageId = storage.id },
                        )
                    }
                }
            }

            StatsCard(state = state)

            if (state.showUpgradePrompt) {
                UpgradeCard(onUpgradeClick = onOpenUpgrade)
            }
        }
    }

    pendingDeleteStorageId?.let { storageId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteStorageId = null },
            title = { Text(stringResource(R.string.stats_space_history_delete_storage_title)) },
            text = { Text(stringResource(R.string.stats_space_history_delete_storage_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteStorage(storageId)
                    pendingDeleteStorageId = null
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteStorageId = null }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun RangeChip(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun StorageChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier.pointerInput(label) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongClick() },
            )
        },
    ) {
        FilterChip(
            selected = selected,
            onClick = {},
            label = { Text(label) },
        )
    }
}

@Composable
private fun SpaceHistoryChart(
    modifier: Modifier = Modifier,
    snapshots: List<eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity>,
    reports: List<eu.darken.sdmse.stats.core.db.ReportEntity>,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SpaceHistoryChartView(context).apply {
                setOnMarkerTapListener { report, screenX, screenY ->
                    SpaceHistoryMarkerTooltip.show(this, report, screenX, screenY)
                }
            }
        },
        update = { chart ->
            // Data-identity guard: only push when the underlying list reference changes so
            // selectedMarkerIndex isn't reset on unrelated recompositions.
            val tag = chart.tag as? ChartDataTag
            if (tag?.snapshots !== snapshots) chart.setData(snapshots)
            if (tag?.reports !== reports) chart.setReports(reports)
            chart.tag = ChartDataTag(snapshots, reports)
        },
    )
}

private data class ChartDataTag(
    val snapshots: List<eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity>,
    val reports: List<eu.darken.sdmse.stats.core.db.ReportEntity>,
)

@Composable
private fun StatsCard(state: SpaceHistoryViewModel.State) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatsRow(
                label = stringResource(R.string.stats_space_history_current_used_label),
                value = state.currentUsed?.let { Formatter.formatShortFileSize(context, it) } ?: "-",
            )
            Spacer(Modifier.height(8.dp))
            StatsRow(
                label = stringResource(R.string.stats_space_history_min_label),
                value = state.minUsed?.let { Formatter.formatShortFileSize(context, it) } ?: "-",
            )
            Spacer(Modifier.height(8.dp))
            StatsRow(
                label = stringResource(R.string.stats_space_history_max_label),
                value = state.maxUsed?.let { Formatter.formatShortFileSize(context, it) } ?: "-",
            )
            Spacer(Modifier.height(8.dp))
            val deltaText = state.deltaUsed?.let { delta ->
                val absDelta = Formatter.formatShortFileSize(context, delta.absoluteValue)
                val signed = when {
                    delta > 0 -> "+$absDelta"
                    delta < 0 -> "-$absDelta"
                    else -> absDelta
                }
                stringResource(
                    R.string.stats_space_history_delta_in_x,
                    signed,
                    stringResource(state.selectedRange.labelRes),
                )
            } ?: "-"
            val deltaColor = when {
                state.deltaUsed == null -> MaterialTheme.colorScheme.onSurfaceVariant
                state.deltaUsed!! > 0 -> MaterialTheme.colorScheme.error
                state.deltaUsed!! < 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            StatsRow(
                label = stringResource(R.string.stats_space_history_delta_label),
                value = deltaText,
                valueColor = deltaColor,
            )
        }
    }
}

@Composable
private fun StatsRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
    }
}

@Composable
private fun UpgradeCard(onUpgradeClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_space_history_upgrade_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.stats_space_history_upgrade_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = onUpgradeClick) {
                    Text(stringResource(CommonR.string.general_upgrade_action))
                }
            }
        }
    }
}

private val SpaceHistoryViewModel.Range.labelRes: Int
    get() = when (this) {
        SpaceHistoryViewModel.Range.DAYS_7 -> R.string.stats_space_history_range_7d
        SpaceHistoryViewModel.Range.DAYS_30 -> R.string.stats_space_history_range_30d
        SpaceHistoryViewModel.Range.DAYS_90 -> R.string.stats_space_history_range_90d
    }

@Preview2
@Composable
private fun SpaceHistoryScreenFreePreview() {
    PreviewWrapper {
        SpaceHistoryScreen(
            stateSource = MutableStateFlow(
                SpaceHistoryViewModel.State(
                    isPro = false,
                    showUpgradePrompt = true,
                    selectedRange = SpaceHistoryViewModel.Range.DAYS_7,
                ),
            ),
        )
    }
}

@Preview2
@Composable
private fun SpaceHistoryScreenProPreview() {
    PreviewWrapper {
        SpaceHistoryScreen(
            stateSource = MutableStateFlow(
                SpaceHistoryViewModel.State(
                    isPro = true,
                    showUpgradePrompt = false,
                    selectedRange = SpaceHistoryViewModel.Range.DAYS_30,
                    currentUsed = 44_000_000_000L,
                    minUsed = 40_000_000_000L,
                    maxUsed = 45_000_000_000L,
                    deltaUsed = 1_200_000_000L,
                ),
            ),
        )
    }
}
