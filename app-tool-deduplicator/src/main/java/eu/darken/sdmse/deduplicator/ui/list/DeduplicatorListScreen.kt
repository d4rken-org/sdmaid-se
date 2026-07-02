package eu.darken.sdmse.deduplicator.ui.list

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material.icons.twotone.GridView
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.common.compose.layout.SdmDeleteAction
import eu.darken.sdmse.common.compose.layout.SdmEmptyState
import eu.darken.sdmse.common.compose.layout.SdmExcludeAction
import eu.darken.sdmse.common.compose.layout.SdmListDefaults
import eu.darken.sdmse.common.compose.layout.SdmLoadingState
import eu.darken.sdmse.common.compose.layout.SdmSelectAllAction
import eu.darken.sdmse.common.compose.layout.SdmSelectionTopAppBar
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.layout.SdmTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.snackbar.ToolListEventHandler
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionDialog
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionMode
import eu.darken.sdmse.deduplicator.ui.list.items.DeduplicatorGridRow
import eu.darken.sdmse.deduplicator.ui.list.items.DeduplicatorLinearRow
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private sealed interface ListDeletionRequest {
    data class Clusters(
        val clusters: List<Duplicate.Cluster>,
        val allowDeleteAll: Boolean,
        val targetCount: Int,
        val freeableSize: Long,
    ) : ListDeletionRequest

    data class Duplicates(
        val duplicates: List<eu.darken.sdmse.deduplicator.core.Duplicate>,
        val detailsClusterId: Duplicate.Cluster.Id?,
    ) : ListDeletionRequest
}

@Parcelize
private data class MixedSelection(
    val dupes: Map<Duplicate.Cluster.Id, Set<Duplicate.Id>> = emptyMap(),
) : Parcelable {
    val isEmpty: Boolean get() = dupes.values.all { it.isEmpty() }
    val fileCount: Int get() = dupes.values.sumOf { it.size }
    val flat: Set<Duplicate.Id> get() = dupes.values.flatten().toSet()

    companion object {
        val Empty = MixedSelection()
    }
}

private fun capFor(totalInCluster: Int, allowDeleteAll: Boolean): Int =
    if (allowDeleteAll) totalInCluster else (totalInCluster - 1).coerceAtLeast(0)

private fun MixedSelection.addClusterTargets(
    clusterId: Duplicate.Cluster.Id,
    targets: Set<Duplicate.Id>,
    cap: Int,
): MixedSelection {
    if (targets.isEmpty() || cap <= 0) return this
    val existing = dupes[clusterId] ?: emptySet()
    val merged = (existing + targets).toList().take(cap).toSet()
    return if (merged == existing) this else copy(dupes = dupes + (clusterId to merged))
}

private fun MixedSelection.toggleClusterTargets(
    clusterId: Duplicate.Cluster.Id,
    targets: Set<Duplicate.Id>,
    cap: Int,
): MixedSelection {
    val existing = dupes[clusterId] ?: emptySet()
    val allCovered = targets.isNotEmpty() && existing.containsAll(targets)
    return if (allCovered) {
        copy(dupes = dupes - clusterId)
    } else {
        addClusterTargets(clusterId, targets, cap)
    }
}

/**
 * Compose-isolated access to [MixedSelection], mirroring [eu.darken.sdmse.common.compose.selection.SelectionState]'s
 * per-id derivedStateOf pattern: passing the raw value into the list composables subscribed every
 * visible row to any selection change, so toggling one duplicate recomposed the whole
 * thumbnail-heavy window. The structured per-cluster shape doesn't map onto SelectionState<T>,
 * hence the bespoke holder with the same isolation semantics.
 */
@Stable
private class MixedSelectionHolder(initial: MixedSelection) {

    var value by mutableStateOf(initial)

    /** Notifies only on the empty <-> non-empty transition. */
    val isActive: Boolean by derivedStateOf { !value.isEmpty }

    /** Notifies only when the selected-file count changes (top-bar "N selected"). */
    val fileCount: Int by derivedStateOf { value.fileCount }

    /** Per-cluster selected duplicates; only rows whose set actually changes recompose. */
    @Composable
    fun selectedDupes(clusterId: Duplicate.Cluster.Id): Set<Duplicate.Id> {
        val state = remember(this, clusterId) { derivedStateOf { value.dupes[clusterId] ?: emptySet() } }
        return state.value
    }

    /** Per-cluster "has any selection" flag for the grid tiles. */
    @Composable
    fun hasSelectedDupes(clusterId: Duplicate.Cluster.Id): Boolean {
        val state = remember(this, clusterId) { derivedStateOf { value.dupes[clusterId]?.isNotEmpty() == true } }
        return state.value
    }

    companion object {
        fun saver(): Saver<MixedSelectionHolder, MixedSelection> = Saver(
            save = { it.value },
            restore = { MixedSelectionHolder(it) },
        )
    }
}

private enum class DupeChange { Add, Toggle }

private data class DupeChangeResult(
    val selection: MixedSelection,
    val capExceeded: Boolean,
)

private fun MixedSelection.changeDupe(
    clusterId: Duplicate.Cluster.Id,
    dupeId: Duplicate.Id,
    cap: Int,
    mode: DupeChange,
): DupeChangeResult {
    val existing = dupes[clusterId] ?: emptySet()
    val nextPer = when (mode) {
        DupeChange.Add -> existing + dupeId
        DupeChange.Toggle -> if (dupeId in existing) existing - dupeId else existing + dupeId
    }
    if (nextPer.size > cap) return DupeChangeResult(this, capExceeded = true)
    val nextDupes = if (nextPer.isEmpty()) dupes - clusterId else dupes + (clusterId to nextPer)
    return DupeChangeResult(copy(dupes = nextDupes), capExceeded = false)
}

@Composable
fun DeduplicatorListScreenHost(
    vm: DeduplicatorListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val snackbarHostState = remember { SnackbarHostState() }

    var deletion by remember { mutableStateOf<ListDeletionRequest?>(null) }

    ToolListEventHandler(
        events = vm.events,
        snackbarHostState = snackbarHostState,
        onShowExclusions = { vm.navTo(ExclusionsListRoute) },
        taskResultDuration = SnackbarDuration.Short,
    ) { event ->
        when (event) {
            is DeduplicatorListViewModel.Event.ConfirmDeletion -> {
                deletion = ListDeletionRequest.Clusters(
                    clusters = event.clusters.toList(),
                    allowDeleteAll = event.allowDeleteAll,
                    targetCount = event.targetCount,
                    freeableSize = event.freeableSize,
                )
            }

            is DeduplicatorListViewModel.Event.ConfirmDupeDeletion -> {
                if (event.duplicates.isNotEmpty()) {
                    deletion = ListDeletionRequest.Duplicates(
                        duplicates = event.duplicates.toList(),
                        detailsClusterId = event.detailsClusterId,
                    )
                }
            }

            else -> Unit
        }
    }

    DeduplicatorListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onClusterClick = { cluster -> vm.showDetails(cluster.identifier) },
        onClusterDelete = { cluster -> vm.deleteClusters(listOf(cluster)) },
        onClusterPreview = { cluster -> vm.previewCluster(cluster) },
        onDuplicateClick = { cluster, dupe -> vm.deleteDuplicate(cluster, dupe) },
        onDuplicatePreview = { cluster, dupe -> vm.previewDuplicate(cluster, dupe) },
        onDeleteDuplicates = { ids -> vm.deleteDuplicates(ids) },
        onExcludeClusters = { clusterIds -> vm.excludeClusterIds(clusterIds) },
        onToggleLayoutMode = vm::toggleLayoutMode,
    )

    deletion?.let { req ->
        val mode = when (req) {
            is ListDeletionRequest.Clusters -> PreviewDeletionMode.Clusters(
                clusters = req.clusters,
                allowDeleteAll = req.allowDeleteAll,
                targetCount = req.targetCount,
                freeableSize = req.freeableSize,
            )

            is ListDeletionRequest.Duplicates -> PreviewDeletionMode.Duplicates(
                duplicates = req.duplicates,
            )
        }
        val detailsCallback: (() -> Unit)? = when (req) {
            is ListDeletionRequest.Clusters -> {
                {
                    deletion = null
                    vm.showDetails(req.clusters.first().identifier)
                }
            }

            is ListDeletionRequest.Duplicates -> req.detailsClusterId?.let { id ->
                {
                    deletion = null
                    vm.showDetails(id)
                }
            }
        }
        PreviewDeletionDialog(
            mode = mode,
            onConfirm = { deleteAll ->
                deletion = null
                when (req) {
                    is ListDeletionRequest.Clusters -> vm.deleteClusters(
                        req.clusters,
                        confirmed = true,
                        deleteAll = deleteAll,
                    )

                    is ListDeletionRequest.Duplicates -> vm.deleteDuplicates(
                        req.duplicates.map { it.identifier }.toSet(),
                        confirmed = true,
                    )
                }
            },
            onDismiss = { deletion = null },
            onPreviewClick = { options -> vm.openPreview(options) },
            onShowDetails = detailsCallback,
        )
    }
}

@Composable
internal fun DeduplicatorListScreen(
    stateSource: StateFlow<DeduplicatorListViewModel.State?> = MutableStateFlow(null),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onClusterClick: (Duplicate.Cluster) -> Unit = {},
    onClusterDelete: (Duplicate.Cluster) -> Unit = {},
    onClusterPreview: (Duplicate.Cluster) -> Unit = {},
    onDuplicateClick: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    onDeleteDuplicates: (Set<Duplicate.Id>) -> Unit = {},
    onExcludeClusters: (Set<Duplicate.Cluster.Id>) -> Unit = {},
    onToggleLayoutMode: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state?.rows
    val layoutMode = state?.layoutMode ?: LayoutMode.GRID
    val allowDeleteAll = state?.allowDeleteAll ?: false

    val selectionHolder = rememberSaveable(saver = MixedSelectionHolder.saver()) {
        MixedSelectionHolder(MixedSelection.Empty)
    }
    var selection by selectionHolder::value
    val snackScope = rememberCoroutineScope()

    val rowsById = rows?.associateBy { it.cluster.identifier } ?: emptyMap()
    val clusterIds = rowsById.keys
    val allDupeIds = rows?.flatMap { row -> row.cluster.groups.flatMap { it.duplicates }.map { it.identifier } }
        ?.toSet() ?: emptySet()

    LaunchedEffect(clusterIds, allDupeIds) {
        // Prune stale selection entries when clusters or duplicates disappear after delete/exclude.
        val prunedDupes = selection.dupes
            .filterKeys { it in clusterIds }
            .mapValues { (_, dupeIds) -> dupeIds intersect allDupeIds }
            .filterValues { it.isNotEmpty() }
        val pruned = MixedSelection(dupes = prunedDupes)
        if (pruned != selection) selection = pruned
    }
    BackHandler(enabled = selectionHolder.isActive) { selection = MixedSelection.Empty }

    val capRejectMsg = stringResource(DeduplicatorR.string.deduplicator_selection_keep_one_required)
    val notifyCapExceeded: () -> Unit = {
        snackScope.launch {
            snackbarHostState.showSnackbar(message = capRejectMsg, duration = SnackbarDuration.Short)
        }
    }

    val subtitle = rows?.let { list ->
        if (state?.progress == null) {
            pluralStringResource(CommonR.plurals.result_x_items, list.size, list.size)
        } else {
            null
        }
    }

    SdmScaffold(
        topBar = {
            if (!selectionHolder.isActive) {
                SdmTopAppBar(
                    title = stringResource(CommonR.string.deduplicator_tool_name),
                    subtitle = subtitle,
                    onNavigateUp = onNavigateUp,
                    actions = {
                        // Hidden during an active scan/delete (legacy parity) — toggling mid-task
                        // would race a settings write against the in-flight operation.
                        val icon = when (layoutMode) {
                            LayoutMode.LINEAR -> Icons.TwoTone.GridView
                            LayoutMode.GRID -> Icons.AutoMirrored.TwoTone.ViewList
                        }
                        SdmTooltipIconButton(
                            icon = icon,
                            label = stringResource(CommonR.string.general_toggle_layout_mode),
                            onClick = onToggleLayoutMode,
                            enabled = state?.progress == null,
                        )
                    },
                )
            } else {
                val safeTargetUnion = rows.orEmpty().flatMap { it.deleteTargetIds }.toSet()
                SdmSelectionTopAppBar(
                    selectedCount = selectionHolder.fileCount,
                    onClearSelection = { selection = MixedSelection.Empty },
                    actions = {
                        SdmDeleteAction(onClick = {
                            val ids = selection.flat
                            if (ids.isNotEmpty()) onDeleteDuplicates(ids)
                        })
                        SdmExcludeAction(onClick = {
                            // Cluster-level exclusion (legacy parity) — excludes whole clusters that
                            // have a selected duplicate, so they disappear from the list.
                            val clusterIds = selection.dupes.keys
                            selection = MixedSelection.Empty
                            if (clusterIds.isNotEmpty()) onExcludeClusters(clusterIds)
                        })
                        SdmSelectAllAction(
                            visible = safeTargetUnion.isNotEmpty() && !selection.flat.containsAll(safeTargetUnion),
                            onClick = {
                                val nextDupes = rows.orEmpty()
                                    .associate { it.cluster.identifier to it.deleteTargetIds }
                                    .filterValues { it.isNotEmpty() }
                                selection = MixedSelection(dupes = nextDupes)
                            },
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ProgressOverlay(
                data = state?.progress,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    rows == null -> SdmLoadingState()

                    rows.isEmpty() -> SdmEmptyState()

                    else -> {
                        val onClusterLongPress: (Duplicate.Cluster) -> Unit = { cluster ->
                            val row = rowsById[cluster.identifier]
                            val targets = row?.deleteTargetIds ?: emptySet()
                            val cap = capFor(cluster.count, allowDeleteAll)
                            selection = selection.addClusterTargets(cluster.identifier, targets, cap)
                        }
                        val toggleCluster: (Duplicate.Cluster) -> Unit = { cluster ->
                            val row = rowsById[cluster.identifier]
                            val targets = row?.deleteTargetIds ?: emptySet()
                            val cap = capFor(cluster.count, allowDeleteAll)
                            selection = selection.toggleClusterTargets(cluster.identifier, targets, cap)
                        }
                        // No-selection tap runs the region's primary action; while selecting, any region toggles.
                        val onClusterDeleteTap: (Duplicate.Cluster) -> Unit = { cluster ->
                            if (selection.isEmpty) onClusterDelete(cluster) else toggleCluster(cluster)
                        }
                        val onClusterDetailsTap: (Duplicate.Cluster) -> Unit = { cluster ->
                            if (selection.isEmpty) onClusterClick(cluster) else toggleCluster(cluster)
                        }
                        val onClusterPreviewTap: (Duplicate.Cluster) -> Unit = { cluster ->
                            if (selection.isEmpty) onClusterPreview(cluster) else toggleCluster(cluster)
                        }
                        val applyDupeChange: (Duplicate.Cluster, Duplicate, DupeChange) -> Unit = { cluster, dupe, mode ->
                            val cap = capFor(cluster.count, allowDeleteAll)
                            val result = selection.changeDupe(
                                clusterId = cluster.identifier,
                                dupeId = dupe.identifier,
                                cap = cap,
                                mode = mode,
                            )
                            if (result.capExceeded) {
                                notifyCapExceeded()
                            } else {
                                selection = result.selection
                            }
                        }
                        val onDuplicateLongPress: (Duplicate.Cluster, Duplicate) -> Unit = { cluster, dupe ->
                            applyDupeChange(cluster, dupe, DupeChange.Add)
                        }
                        val onDuplicateTap: (Duplicate.Cluster, Duplicate) -> Unit = { cluster, dupe ->
                            if (selection.isEmpty) {
                                onDuplicateClick(cluster, dupe)
                            } else {
                                applyDupeChange(cluster, dupe, DupeChange.Toggle)
                            }
                        }
                        when (layoutMode) {
                            LayoutMode.LINEAR -> LinearList(
                                rows = rows,
                                selection = selectionHolder,
                                onHeaderClick = onClusterDeleteTap,
                                onThumbnailClick = onClusterPreviewTap,
                                onClusterLongPress = onClusterLongPress,
                                onDuplicateTap = onDuplicateTap,
                                onDuplicateLongPress = onDuplicateLongPress,
                                onDuplicatePreview = onDuplicatePreview,
                            )

                            LayoutMode.GRID -> GridList(
                                rows = rows,
                                selection = selectionHolder,
                                onThumbnailClick = onClusterDeleteTap,
                                onCaptionClick = onClusterDetailsTap,
                                onPreviewButtonClick = onClusterPreviewTap,
                                onClusterLongPress = onClusterLongPress,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinearList(
    rows: List<DeduplicatorListViewModel.DeduplicatorListRow>,
    selection: MixedSelectionHolder,
    onHeaderClick: (Duplicate.Cluster) -> Unit,
    onThumbnailClick: (Duplicate.Cluster) -> Unit,
    onClusterLongPress: (Duplicate.Cluster) -> Unit,
    onDuplicateTap: (Duplicate.Cluster, Duplicate) -> Unit,
    onDuplicateLongPress: (Duplicate.Cluster, Duplicate) -> Unit,
    onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit,
) {
    val selectionActive = selection.isActive
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = SdmListDefaults.FullWidthContentPadding,
    ) {
        items(rows, key = { it.cluster.identifier.value }) { row ->
            val selectedDupes = selection.selectedDupes(row.cluster.identifier)
            DeduplicatorLinearRow(
                row = row,
                selected = false,
                selectionActive = selectionActive,
                selectedDupes = selectedDupes,
                onHeaderClick = { onHeaderClick(row.cluster) },
                onLongClick = { onClusterLongPress(row.cluster) },
                onThumbnailClick = { onThumbnailClick(row.cluster) },
                onDuplicateClick = { dupe -> onDuplicateTap(row.cluster, dupe) },
                onDuplicateLongClick = { dupe -> onDuplicateLongPress(row.cluster, dupe) },
                onDuplicatePreviewClick = { dupe -> onDuplicatePreview(row.cluster, dupe) },
            )
        }
    }
}

@Composable
private fun GridList(
    rows: List<DeduplicatorListViewModel.DeduplicatorListRow>,
    selection: MixedSelectionHolder,
    onThumbnailClick: (Duplicate.Cluster) -> Unit,
    onCaptionClick: (Duplicate.Cluster) -> Unit,
    onPreviewButtonClick: (Duplicate.Cluster) -> Unit,
    onClusterLongPress: (Duplicate.Cluster) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val context = LocalContext.current
        val spanCount = remember(maxWidth) { context.getSpanCount(widthDp = 144).coerceAtLeast(2) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(spanCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = SdmListDefaults.GridTileContentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(rows, key = { it.cluster.identifier.value }) { row ->
                val isSelected = selection.hasSelectedDupes(row.cluster.identifier)
                DeduplicatorGridRow(
                    row = row,
                    selected = isSelected,
                    onThumbnailClick = { onThumbnailClick(row.cluster) },
                    onCaptionClick = { onCaptionClick(row.cluster) },
                    onPreviewButtonClick = { onPreviewButtonClick(row.cluster) },
                    onLongClick = { onClusterLongPress(row.cluster) },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun DeduplicatorListScreenLoadingPreview() {
    PreviewWrapper {
        DeduplicatorListScreen(
            stateSource = MutableStateFlow(null),
        )
    }
}

@Preview2
@Composable
private fun DeduplicatorListScreenEmptyPreview() {
    PreviewWrapper {
        DeduplicatorListScreen(
            stateSource = MutableStateFlow(
                DeduplicatorListViewModel.State(
                    rows = emptyList(),
                    layoutMode = LayoutMode.GRID,
                )
            ),
        )
    }
}
