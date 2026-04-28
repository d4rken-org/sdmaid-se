package eu.darken.sdmse.deduplicator.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
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

    data class Duplicate(
        val cluster: eu.darken.sdmse.deduplicator.core.Duplicate.Cluster,
        val duplicate: eu.darken.sdmse.deduplicator.core.Duplicate,
    ) : ListDeletionRequest
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
                    val dupe = event.duplicates.firstOrNull() ?: return@collect
                    deletion = ListDeletionRequest.Duplicate(cluster = event.cluster, duplicate = dupe)
                }

                is DeduplicatorListViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
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
        onDeleteSelected = { ids, rows ->
            val clusters = rows.filter { it.cluster.identifier in ids }.map { it.cluster }
            vm.deleteClusters(clusters)
        },
        onExcludeSelected = { ids, rows ->
            val clusters = rows.filter { it.cluster.identifier in ids }.map { it.cluster }
            vm.excludeClusters(clusters)
        },
        onToggleLayoutMode = vm::toggleLayoutMode,
    )

    deletion?.let { req ->
        val mode = when (req) {
            is ListDeletionRequest.Clusters -> PreviewDeletionMode.Clusters(
                clusters = req.clusters,
                allowDeleteAll = req.allowDeleteAll,
            )

            is ListDeletionRequest.Duplicate -> PreviewDeletionMode.Duplicates(
                duplicates = listOf(req.duplicate),
            )
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

                    is ListDeletionRequest.Duplicate -> vm.deleteDuplicate(
                        req.cluster,
                        req.duplicate,
                        confirmed = true,
                    )
                }
            },
            onDismiss = { deletion = null },
            onPreviewClick = { options -> vm.openPreview(options) },
            onShowDetails = when (req) {
                is ListDeletionRequest.Clusters -> {
                    {
                        deletion = null
                        vm.showDetails(req.clusters.first().identifier)
                    }
                }

                is ListDeletionRequest.Duplicate -> {
                    {
                        deletion = null
                        vm.showDetails(req.cluster.identifier)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeduplicatorListScreen(
    stateSource: StateFlow<DeduplicatorListViewModel.State?> = MutableStateFlow(null),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onClusterClick: (Duplicate.Cluster) -> Unit = {},
    onClusterPreview: (Duplicate.Cluster) -> Unit = {},
    onDuplicateClick: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    onDeleteSelected: (Set<Duplicate.Cluster.Id>, List<DeduplicatorListViewModel.DeduplicatorListRow>) -> Unit = { _, _ -> },
    onExcludeSelected: (Set<Duplicate.Cluster.Id>, List<DeduplicatorListViewModel.DeduplicatorListRow>) -> Unit = { _, _ -> },
    onToggleLayoutMode: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state?.rows
    val layoutMode = state?.layoutMode ?: LayoutMode.GRID

    var selection by remember { mutableStateOf<Set<Duplicate.Cluster.Id>>(emptySet()) }
    val rowIds = rows?.map { it.cluster.identifier }?.toSet() ?: emptySet()
    LaunchedEffect(rowIds) {
        // Prune stale selection entries when clusters disappear (after delete/exclude).
        selection = selection intersect rowIds
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val subtitle = rows?.let { list ->
        if (state?.progress == null) {
            pluralStringResource(CommonR.plurals.result_x_items, list.size, list.size)
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(CommonR.string.deduplicator_tool_name))
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleLayoutMode) {
                            val icon = when (layoutMode) {
                                LayoutMode.LINEAR -> Icons.TwoTone.GridView
                                LayoutMode.GRID -> Icons.AutoMirrored.TwoTone.ViewList
                            }
                            Icon(icon, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val ids = selection
                            onDeleteSelected(ids, rows ?: emptyList())
                        }) {
                            Icon(
                                Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_selected_action),
                            )
                        }
                        IconButton(onClick = {
                            val ids = selection
                            selection = emptySet()
                            onExcludeSelected(ids, rows ?: emptyList())
                        }) {
                            Icon(
                                Icons.TwoTone.Shield,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                        IconButton(onClick = { selection = rowIds }) {
                            Icon(
                                Icons.TwoTone.SelectAll,
                                contentDescription = stringResource(CommonR.string.general_list_select_all_action),
                            )
                        }
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
                    rows == null -> Box(modifier = Modifier.fillMaxSize())

                    rows.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(CommonR.string.general_empty_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        when (layoutMode) {
                            LayoutMode.LINEAR -> LinearList(
                                rows = rows,
                                selection = selection,
                                onClusterClick = onClusterClick,
                                onClusterLongClick = { id -> selection = selection + id },
                                onSelectionToggle = { id ->
                                    selection = if (selection.contains(id)) selection - id else selection + id
                                },
                                onClusterPreview = onClusterPreview,
                                onDuplicateClick = onDuplicateClick,
                                onDuplicatePreview = onDuplicatePreview,
                            )

                            LayoutMode.GRID -> GridList(
                                rows = rows,
                                selection = selection,
                                onClusterClick = onClusterClick,
                                onClusterLongClick = { id -> selection = selection + id },
                                onSelectionToggle = { id ->
                                    selection = if (selection.contains(id)) selection - id else selection + id
                                },
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
    selection: Set<Duplicate.Cluster.Id>,
    onClusterClick: (Duplicate.Cluster) -> Unit,
    onClusterLongClick: (Duplicate.Cluster.Id) -> Unit,
    onSelectionToggle: (Duplicate.Cluster.Id) -> Unit,
    onClusterPreview: (Duplicate.Cluster) -> Unit,
    onDuplicateClick: (Duplicate.Cluster, Duplicate) -> Unit,
    onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(rows, key = { it.cluster.identifier.value }) { row ->
            val isSelected = selection.contains(row.cluster.identifier)
            DeduplicatorLinearRow(
                row = row,
                selected = isSelected,
                selectionActive = selection.isNotEmpty(),
                onClick = {
                    if (selection.isNotEmpty()) onSelectionToggle(row.cluster.identifier)
                    else onClusterClick(row.cluster)
                },
                onLongClick = { onClusterLongClick(row.cluster.identifier) },
                onPreviewClick = { onClusterPreview(row.cluster) },
                onDuplicateClick = { dupe -> onDuplicateClick(row.cluster, dupe) },
                onDuplicatePreviewClick = { dupe -> onDuplicatePreview(row.cluster, dupe) },
            )
        }
    }
}

@Composable
private fun GridList(
    rows: List<DeduplicatorListViewModel.DeduplicatorListRow>,
    selection: Set<Duplicate.Cluster.Id>,
    onClusterClick: (Duplicate.Cluster) -> Unit,
    onClusterLongClick: (Duplicate.Cluster.Id) -> Unit,
    onSelectionToggle: (Duplicate.Cluster.Id) -> Unit,
    onClusterPreview: (Duplicate.Cluster) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val context = LocalContext.current
        val spanCount = remember(maxWidth) { context.getSpanCount(widthDp = 144).coerceAtLeast(2) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(spanCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(rows, key = { it.cluster.identifier.value }) { row ->
                val isSelected = selection.contains(row.cluster.identifier)
                DeduplicatorGridRow(
                    row = row,
                    selected = isSelected,
                    selectionActive = selection.isNotEmpty(),
                    onClick = {
                        if (selection.isNotEmpty()) onSelectionToggle(row.cluster.identifier)
                        else onClusterClick(row.cluster)
                    },
                    onLongClick = { onClusterLongClick(row.cluster.identifier) },
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
