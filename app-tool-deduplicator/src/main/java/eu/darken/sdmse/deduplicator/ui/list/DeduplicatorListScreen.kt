package eu.darken.sdmse.deduplicator.ui.list

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.SdmDeleteAction
import eu.darken.sdmse.common.compose.layout.SdmEmptyState
import eu.darken.sdmse.common.compose.layout.SdmExcludeAction
import eu.darken.sdmse.common.compose.layout.SdmListDefaults
import eu.darken.sdmse.common.compose.layout.SdmLoadingState
import eu.darken.sdmse.common.compose.layout.SdmSelectAllAction
import eu.darken.sdmse.common.compose.layout.SdmSelectionTopAppBar
import eu.darken.sdmse.common.compose.layout.SdmTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
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

private sealed interface ListDeletionRequest {
    data class Clusters(
        val clusters: List<Duplicate.Cluster>,
        val allowDeleteAll: Boolean,
    ) : ListDeletionRequest

    data class Duplicates(
        val duplicates: List<eu.darken.sdmse.deduplicator.core.Duplicate>,
        val detailsClusterId: Duplicate.Cluster.Id?,
    ) : ListDeletionRequest
}

private sealed interface ListSelection {
    val isEmpty: Boolean
    val count: Int

    data object None : ListSelection {
        override val isEmpty = true
        override val count = 0
    }

    data class Clusters(val ids: Set<Duplicate.Cluster.Id>) : ListSelection {
        override val isEmpty get() = ids.isEmpty()
        override val count get() = ids.size
    }

    data class Dupes(val idsByCluster: Map<Duplicate.Cluster.Id, Set<Duplicate.Id>>) : ListSelection {
        override val isEmpty get() = idsByCluster.values.all { it.isEmpty() }
        override val count get() = idsByCluster.values.sumOf { it.size }
        val flat: Set<Duplicate.Id> get() = idsByCluster.values.flatten().toSet()
    }
}

private fun ListSelection.Clusters.toggle(id: Duplicate.Cluster.Id): ListSelection {
    val next = if (id in ids) ids - id else ids + id
    return if (next.isEmpty()) ListSelection.None else copy(ids = next)
}

private fun ListSelection.Clusters.addAll(extra: Set<Duplicate.Cluster.Id>): ListSelection =
    if (extra.isEmpty()) this else copy(ids = ids + extra)

private fun ListSelection.Dupes.toggle(clusterId: Duplicate.Cluster.Id, dupeId: Duplicate.Id): ListSelection {
    val perCluster = idsByCluster[clusterId] ?: emptySet()
    val nextPer = if (dupeId in perCluster) perCluster - dupeId else perCluster + dupeId
    val nextMap = if (nextPer.isEmpty()) idsByCluster - clusterId else idsByCluster + (clusterId to nextPer)
    return if (nextMap.isEmpty()) ListSelection.None else copy(idsByCluster = nextMap)
}

private fun ListSelection.Dupes.addOne(clusterId: Duplicate.Cluster.Id, dupeId: Duplicate.Id): ListSelection {
    val perCluster = idsByCluster[clusterId] ?: emptySet()
    val nextPer = perCluster + dupeId
    return copy(idsByCluster = idsByCluster + (clusterId to nextPer))
}

@Composable
fun DeduplicatorListScreenHost(
    vm: DeduplicatorListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var deletion by remember { mutableStateOf<ListDeletionRequest?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DeduplicatorListViewModel.Event.ConfirmDeletion -> {
                    deletion = ListDeletionRequest.Clusters(
                        clusters = event.clusters.toList(),
                        allowDeleteAll = event.allowDeleteAll,
                    )
                }

                is DeduplicatorListViewModel.Event.ConfirmDupeDeletion -> {
                    if (event.duplicates.isEmpty()) return@collect
                    deletion = ListDeletionRequest.Duplicates(
                        duplicates = event.duplicates.toList(),
                        detailsClusterId = event.detailsClusterId,
                    )
                }

                is DeduplicatorListViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Short,
                    )
                }

                is DeduplicatorListViewModel.Event.ExclusionsCreated -> snackScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            CommonR.plurals.exclusion_x_new_exclusions,
                            event.count,
                            event.count,
                        ),
                        actionLabel = context.getString(CommonR.string.general_view_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.navTo(ExclusionsListRoute)
                }
            }
        }
    }

    DeduplicatorListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onClusterClick = { cluster -> vm.showDetails(cluster.identifier) },
        onClusterPreview = { cluster -> vm.previewCluster(cluster) },
        onDuplicateClick = { cluster, dupe -> vm.deleteDuplicate(cluster, dupe) },
        onDuplicatePreview = { cluster, dupe -> vm.previewDuplicate(cluster, dupe) },
        onDeleteClusters = { ids, rows ->
            val clusters = rows.filter { it.cluster.identifier in ids }.map { it.cluster }
            vm.deleteClusters(clusters)
        },
        onExcludeClusters = { ids, rows ->
            val clusters = rows.filter { it.cluster.identifier in ids }.map { it.cluster }
            vm.excludeClusters(clusters)
        },
        onDeleteDuplicates = { ids -> vm.deleteDuplicates(ids) },
        onExcludeDuplicates = { ids -> vm.excludeDuplicates(ids) },
        onToggleLayoutMode = vm::toggleLayoutMode,
    )

    deletion?.let { req ->
        val mode = when (req) {
            is ListDeletionRequest.Clusters -> PreviewDeletionMode.Clusters(
                clusters = req.clusters,
                allowDeleteAll = req.allowDeleteAll,
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
    onClusterPreview: (Duplicate.Cluster) -> Unit = {},
    onDuplicateClick: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    onDeleteClusters: (Set<Duplicate.Cluster.Id>, List<DeduplicatorListViewModel.DeduplicatorListRow>) -> Unit = { _, _ -> },
    onExcludeClusters: (Set<Duplicate.Cluster.Id>, List<DeduplicatorListViewModel.DeduplicatorListRow>) -> Unit = { _, _ -> },
    onDeleteDuplicates: (Set<Duplicate.Id>) -> Unit = {},
    onExcludeDuplicates: (Set<Duplicate.Id>) -> Unit = {},
    onToggleLayoutMode: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state?.rows
    val layoutMode = state?.layoutMode ?: LayoutMode.GRID

    var selection by remember { mutableStateOf<ListSelection>(ListSelection.None) }

    val clusterIds = rows?.map { it.cluster.identifier }?.toSet() ?: emptySet()
    val allDupeIds = rows?.flatMap { row -> row.cluster.groups.flatMap { it.duplicates }.map { it.identifier } }
        ?.toSet() ?: emptySet()

    LaunchedEffect(clusterIds, allDupeIds) {
        // Prune stale selection entries when clusters or duplicates disappear after delete/exclude.
        selection = when (val sel = selection) {
            ListSelection.None -> sel
            is ListSelection.Clusters -> {
                val pruned = sel.ids intersect clusterIds
                if (pruned.isEmpty()) ListSelection.None else ListSelection.Clusters(pruned)
            }

            is ListSelection.Dupes -> {
                val pruned = sel.idsByCluster
                    .filterKeys { it in clusterIds }
                    .mapValues { (_, dupeIds) -> dupeIds intersect allDupeIds }
                    .filterValues { it.isNotEmpty() }
                if (pruned.isEmpty()) ListSelection.None else ListSelection.Dupes(pruned)
            }
        }
    }
    BackHandler(enabled = !selection.isEmpty) { selection = ListSelection.None }

    val subtitle = rows?.let { list ->
        if (state?.progress == null) {
            pluralStringResource(CommonR.plurals.result_x_items, list.size, list.size)
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            when (val sel = selection) {
                ListSelection.None -> SdmTopAppBar(
                    title = stringResource(CommonR.string.deduplicator_tool_name),
                    subtitle = subtitle,
                    onNavigateUp = onNavigateUp,
                    actions = {
                        IconButton(onClick = onToggleLayoutMode) {
                            val icon = when (layoutMode) {
                                LayoutMode.LINEAR -> Icons.TwoTone.GridView
                                LayoutMode.GRID -> Icons.AutoMirrored.TwoTone.ViewList
                            }
                            Icon(
                                icon,
                                contentDescription = stringResource(CommonR.string.general_toggle_layout_mode),
                            )
                        }
                    },
                )

                is ListSelection.Clusters -> SdmSelectionTopAppBar(
                    selectedCount = sel.count,
                    onClearSelection = { selection = ListSelection.None },
                    actions = {
                        SdmDeleteAction(onClick = {
                            onDeleteClusters(sel.ids, rows ?: emptyList())
                        })
                        SdmExcludeAction(onClick = {
                            val ids = sel.ids
                            selection = ListSelection.None
                            onExcludeClusters(ids, rows ?: emptyList())
                        })
                        SdmSelectAllAction(
                            visible = clusterIds.isNotEmpty() && !sel.ids.containsAll(clusterIds),
                            onClick = { selection = ListSelection.Clusters(clusterIds) },
                        )
                    },
                )

                is ListSelection.Dupes -> {
                    val safeTargets = rows
                        ?.associate { it.cluster.identifier to it.deleteTargetIds }
                        ?.filterValues { it.isNotEmpty() }
                        ?: emptyMap()
                    val safeFlat = safeTargets.values.flatten().toSet()
                    SdmSelectionTopAppBar(
                        selectedCount = sel.count,
                        onClearSelection = { selection = ListSelection.None },
                        actions = {
                            SdmDeleteAction(onClick = {
                                onDeleteDuplicates(sel.flat)
                            })
                            SdmExcludeAction(onClick = {
                                val ids = sel.flat
                                selection = ListSelection.None
                                onExcludeDuplicates(ids)
                            })
                            SdmSelectAllAction(
                                visible = safeFlat.isNotEmpty() && !sel.flat.containsAll(safeFlat),
                                onClick = { selection = ListSelection.Dupes(safeTargets) },
                            )
                        },
                    )
                }
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
                        val onClusterLongPress: (Duplicate.Cluster.Id) -> Unit = { id ->
                            selection = when (val sel = selection) {
                                ListSelection.None, is ListSelection.Dupes -> ListSelection.Clusters(setOf(id))
                                is ListSelection.Clusters -> sel.addAll(setOf(id))
                            }
                        }
                        val onClusterTap: (Duplicate.Cluster) -> Unit = { cluster ->
                            when (val sel = selection) {
                                ListSelection.None, is ListSelection.Dupes -> onClusterClick(cluster)
                                is ListSelection.Clusters -> selection = sel.toggle(cluster.identifier)
                            }
                        }
                        val onDuplicateLongPress: (Duplicate.Cluster, Duplicate) -> Unit = { cluster, dupe ->
                            selection = when (val sel = selection) {
                                ListSelection.None, is ListSelection.Clusters ->
                                    ListSelection.Dupes(mapOf(cluster.identifier to setOf(dupe.identifier)))

                                is ListSelection.Dupes -> sel.addOne(cluster.identifier, dupe.identifier)
                            }
                        }
                        val onDuplicateTap: (Duplicate.Cluster, Duplicate) -> Unit = { cluster, dupe ->
                            when (val sel = selection) {
                                ListSelection.None -> onDuplicateClick(cluster, dupe)
                                is ListSelection.Clusters -> { /* sticky mode — ignore */ }
                                is ListSelection.Dupes -> selection = sel.toggle(cluster.identifier, dupe.identifier)
                            }
                        }
                        when (layoutMode) {
                            LayoutMode.LINEAR -> LinearList(
                                rows = rows,
                                selection = selection,
                                onClusterTap = onClusterTap,
                                onClusterLongPress = onClusterLongPress,
                                onClusterPreview = onClusterPreview,
                                onDuplicateTap = onDuplicateTap,
                                onDuplicateLongPress = onDuplicateLongPress,
                                onDuplicatePreview = onDuplicatePreview,
                            )

                            LayoutMode.GRID -> GridList(
                                rows = rows,
                                selection = selection,
                                onClusterTap = onClusterTap,
                                onClusterLongPress = onClusterLongPress,
                                onClusterPreview = onClusterPreview,
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
    selection: ListSelection,
    onClusterTap: (Duplicate.Cluster) -> Unit,
    onClusterLongPress: (Duplicate.Cluster.Id) -> Unit,
    onClusterPreview: (Duplicate.Cluster) -> Unit,
    onDuplicateTap: (Duplicate.Cluster, Duplicate) -> Unit,
    onDuplicateLongPress: (Duplicate.Cluster, Duplicate) -> Unit,
    onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit,
) {
    val selectionActive = !selection.isEmpty
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = SdmListDefaults.FullWidthContentPadding,
    ) {
        items(rows, key = { it.cluster.identifier.value }) { row ->
            val isClusterSelected = when (selection) {
                is ListSelection.Clusters -> row.cluster.identifier in selection.ids
                else -> false
            }
            val selectedDupes = when (selection) {
                is ListSelection.Dupes -> selection.idsByCluster[row.cluster.identifier] ?: emptySet()
                else -> emptySet()
            }
            DeduplicatorLinearRow(
                row = row,
                selected = isClusterSelected,
                selectionActive = selectionActive,
                selectedDupes = selectedDupes,
                onClick = { onClusterTap(row.cluster) },
                onLongClick = { onClusterLongPress(row.cluster.identifier) },
                onPreviewClick = { onClusterPreview(row.cluster) },
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
    selection: ListSelection,
    onClusterTap: (Duplicate.Cluster) -> Unit,
    onClusterLongPress: (Duplicate.Cluster.Id) -> Unit,
    onClusterPreview: (Duplicate.Cluster) -> Unit,
) {
    val selectionActive = !selection.isEmpty
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
                val isSelected = when (selection) {
                    is ListSelection.Clusters -> row.cluster.identifier in selection.ids
                    else -> false
                }
                DeduplicatorGridRow(
                    row = row,
                    selected = isSelected,
                    selectionActive = selectionActive,
                    onClick = { onClusterTap(row.cluster) },
                    onLongClick = { onClusterLongPress(row.cluster.identifier) },
                    onPreviewClick = { onClusterPreview(row.cluster) },
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
