package eu.darken.sdmse.deduplicator.ui.details

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.FormatListBulleted
import androidx.compose.material.icons.twotone.Folder
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.SdmDeleteAction
import eu.darken.sdmse.common.compose.layout.SdmEmptyState
import eu.darken.sdmse.common.compose.layout.SdmExcludeAction
import eu.darken.sdmse.common.compose.layout.SdmLoadingState
import eu.darken.sdmse.common.compose.layout.SdmScrollableTabStrip
import eu.darken.sdmse.common.compose.layout.SdmSelectAllAction
import eu.darken.sdmse.common.compose.layout.SdmSelectionTopAppBar
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.layout.SdmTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.details.DeduplicatorDetailsViewModel.DeleteTarget
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterContent
import eu.darken.sdmse.deduplicator.ui.details.cluster.buildClusterElements
import eu.darken.sdmse.deduplicator.ui.details.tour.DeduplicatorDetailsTour
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private data class PendingDetailsDeletion(
    val event: DeduplicatorDetailsViewModel.Event.ConfirmDeletion,
)

@Composable
fun DeduplicatorDetailsScreenHost(
    route: DeduplicatorDetailsRoute,
    vm: DeduplicatorDetailsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val undoActionLabel = stringResource(CommonR.string.general_undo_action)
    val viewActionLabel = stringResource(CommonR.string.general_view_action)

    var pendingDeletion by remember { mutableStateOf<PendingDetailsDeletion?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DeduplicatorDetailsViewModel.Event.ConfirmDeletion -> {
                    pendingDeletion = PendingDetailsDeletion(event)
                }

                is DeduplicatorDetailsViewModel.Event.OpenDuplicate -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (_: ActivityNotFoundException) {
                        snackScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(CommonR.string.general_error_no_compatible_app_found_msg),
                                duration = SnackbarDuration.Long,
                            )
                        }
                    }
                }

                is DeduplicatorDetailsViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Short,
                    )
                }

                is DeduplicatorDetailsViewModel.Event.ExclusionsCreated -> snackScope.launch {
                    val message = context.resources.getQuantityString(
                        CommonR.plurals.exclusion_x_new_exclusions,
                        event.count,
                        event.count,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.onUndoExclude(event.undo, event.restoreTarget)
                    }
                }

                is DeduplicatorDetailsViewModel.Event.SelectionExclusionsCreated -> snackScope.launch {
                    val message = context.resources.getQuantityString(
                        CommonR.plurals.exclusion_x_new_exclusions,
                        event.count,
                        event.count,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = viewActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.onShowExclusions()
                    }
                }
            }
        }
    }

    DeduplicatorDetailsScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onPageChanged = vm::updatePage,
        onToggleDirectoryView = vm::toggleDirectoryView,
        onCollapseToggle = vm::toggleDirectoryCollapse,
        onClusterDelete = { id -> vm.deleteCluster(id) },
        onClusterExclude = { id -> vm.excludeCluster(id) },
        onGroupDelete = { clusterId, groupId -> vm.deleteGroup(clusterId, groupId) },
        onGroupView = { group, position -> vm.previewGroup(group, position) },
        onDuplicatePreview = { dupe -> vm.previewDuplicate(dupe) },
        onDuplicateDelete = { clusterId, dupeId -> vm.deleteDuplicates(clusterId, listOf(dupeId)) },
        onDirectoryDeleteAll = { clusterId, dirGroup ->
            vm.deleteDuplicates(clusterId, dirGroup.duplicates.map { it.identifier })
        },
        onDeleteSelectedDuplicates = { clusterId, ids -> vm.deleteDuplicates(clusterId, ids) },
        onExcludeSelectedDuplicates = { clusterId, ids -> vm.excludeDuplicates(clusterId, ids) },
    )

    pendingDeletion?.let { pending ->
        PreviewDeletionDialog(
            mode = pending.event.mode,
            onConfirm = { deleteAll ->
                pendingDeletion = null
                when (val target = pending.event.target) {
                    is DeleteTarget.ClusterTarget -> vm.deleteCluster(
                        clusterId = target.id,
                        confirmed = true,
                        deleteAll = deleteAll,
                    )

                    is DeleteTarget.GroupTarget -> vm.deleteGroup(
                        clusterId = pending.event.clusterId,
                        groupId = target.id,
                        confirmed = true,
                        deleteAll = deleteAll,
                    )

                    is DeleteTarget.DuplicateTargets -> vm.deleteDuplicates(
                        clusterId = pending.event.clusterId,
                        ids = target.ids,
                        confirmed = true,
                    )
                }
            },
            onDismiss = { pendingDeletion = null },
            onPreviewClick = { options -> vm.navTo(eu.darken.sdmse.common.previews.PreviewRoute(options = options)) },
            // Single-duplicate delete dialog offers "Open" (open in external app) — legacy parity.
            onOpen = (pending.event.mode as? eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionMode.Duplicates)
                ?.duplicates?.singleOrNull()
                ?.let { dupe -> { pendingDeletion = null; vm.openDuplicate(dupe) } },
        )
    }
}

@Composable
internal fun DeduplicatorDetailsScreen(
    stateSource: StateFlow<DeduplicatorDetailsViewModel.State?> = MutableStateFlow(null),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onPageChanged: (Duplicate.Cluster.Id) -> Unit = {},
    onToggleDirectoryView: () -> Unit = {},
    onCollapseToggle: (Duplicate.Cluster.Id, eu.darken.sdmse.deduplicator.ui.details.cluster.DirectoryGroup.Id) -> Unit = { _, _ -> },
    onClusterDelete: (Duplicate.Cluster.Id) -> Unit = {},
    onClusterExclude: (Duplicate.Cluster.Id) -> Unit = {},
    onGroupDelete: (Duplicate.Cluster.Id, Duplicate.Group.Id) -> Unit = { _, _ -> },
    onGroupView: (Duplicate.Group, Int) -> Unit = { _, _ -> },
    onDuplicatePreview: (Duplicate) -> Unit = {},
    onDuplicateDelete: (Duplicate.Cluster.Id, Duplicate.Id) -> Unit = { _, _ -> },
    onDirectoryDeleteAll: (Duplicate.Cluster.Id, eu.darken.sdmse.deduplicator.ui.details.cluster.DirectoryGroup) -> Unit = { _, _ -> },
    onDeleteSelectedDuplicates: (Duplicate.Cluster.Id, Set<Duplicate.Id>) -> Unit = { _, _ -> },
    onExcludeSelectedDuplicates: (Duplicate.Cluster.Id, Set<Duplicate.Id>) -> Unit = { _, _ -> },
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val current = state
    val items = current?.items ?: emptyList()
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { items.size })

    var selection by remember { mutableStateOf<Set<Duplicate.Id>>(emptySet()) }

    // drop(1) skips the initial currentPage=0 emission so the scroll-to-target effect below
    // isn't clobbered by a spurious onPageChanged(items[0]) before it can scroll.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                selection = emptySet()
                items.getOrNull(page)?.identifier?.let(onPageChanged)
            }
    }

    LaunchedEffect(current?.target, items) {
        val target = current?.target ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.identifier == target }
        if (idx != -1 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    val currentCluster = items.getOrNull(pagerState.currentPage)
    val liveDupeIds = remember(currentCluster) {
        currentCluster?.groups?.flatMap { g -> g.duplicates.map { it.identifier } }?.toSet() ?: emptySet()
    }
    LaunchedEffect(currentCluster?.identifier, liveDupeIds) {
        selection = selection intersect liveDupeIds
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val allowDeleteAll = current?.allowDeleteAll == true
    val maxSelection = remember(currentCluster, allowDeleteAll) {
        val total = currentCluster?.groups?.sumOf { it.duplicates.size } ?: 0
        if (allowDeleteAll) total else (total - 1).coerceAtLeast(0)
    }

    val tourController = LocalGuidedTourController.current
    // Pin tour targets at start: cluster contents can re-sort after a delete and we don't want
    // the bubble to jump to a different row mid-tour.
    var tourStartAttempted by remember { mutableStateOf(false) }
    var tourClusterId by remember { mutableStateOf<Duplicate.Cluster.Id?>(null) }
    var tourDeleteMarkRowId by remember { mutableStateOf<Duplicate.Id?>(null) }
    // Dedicated LazyListState for the toured cluster's LazyColumn so prepareDeleteMark can
    // scroll the delete-marker row into view even if it's below the fold.
    val tourListState = rememberLazyListState()
    val tourDef = remember(items, tourClusterId, tourDeleteMarkRowId) {
        DeduplicatorDetailsTour.definition(
            prepareDeleteMark = {
                val cluster = items.firstOrNull { it.identifier == tourClusterId } ?: return@definition
                val markId = tourDeleteMarkRowId ?: return@definition
                val elements = buildClusterElements(
                    cluster = cluster,
                    isDirectoryView = false,
                    collapsed = emptySet(),
                )
                val idx = elements.indexOfFirst { el ->
                    el is eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterElement.DuplicateRow &&
                        el.duplicate.identifier == markId
                }
                if (idx >= 0) tourListState.animateScrollToItem(idx)
            },
        )
    }
    val tourEligible = items.isNotEmpty() && current?.isDirectoryView == false
    LaunchedEffect(tourEligible) {
        if (!tourEligible || tourStartAttempted) return@LaunchedEffect
        // Anchor on the route target if one was supplied (deep link into a specific cluster);
        // otherwise fall back to the first cluster. Avoids depending on pagerState.currentPage,
        // which may not have settled when this effect runs.
        val anchorClusterId = current?.target ?: items.firstOrNull()?.identifier
        ?: return@LaunchedEffect
        val cluster = items.firstOrNull { it.identifier == anchorClusterId } ?: return@LaunchedEffect
        // Find a group that has a real keeper-vs-non-keeper split, so the row-level marker copy
        // is accurate. Skip groups without a keeper (no marker shown) or with only one duplicate.
        val groupWithMark = cluster.groups
            .sortedByDescending { it.totalSize }
            .firstOrNull { it.keeperIdentifier != null && it.duplicates.size >= 2 }
            ?: return@LaunchedEffect
        val keeperId = groupWithMark.keeperIdentifier ?: return@LaunchedEffect
        val deleteRow = groupWithMark.duplicates.firstOrNull { it.identifier != keeperId }
            ?: return@LaunchedEffect
        tourStartAttempted = true
        if (!tourController.shouldStart(tourDef)) return@LaunchedEffect
        tourClusterId = cluster.identifier
        tourDeleteMarkRowId = deleteRow.identifier
        tourController.start(tourDef)
    }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                SdmTopAppBar(
                    title = stringResource(CommonR.string.deduplicator_tool_name),
                    subtitle = stringResource(CommonR.string.general_details_label),
                    onNavigateUp = onNavigateUp,
                    actions = {
                        val icon = if (current?.isDirectoryView == true) {
                            Icons.AutoMirrored.TwoTone.FormatListBulleted
                        } else {
                            Icons.TwoTone.Folder
                        }
                        val label = stringResource(
                            if (current?.isDirectoryView == true) {
                                DeduplicatorR.string.deduplicator_view_mode_groups_label
                            } else {
                                DeduplicatorR.string.deduplicator_view_mode_directories_label
                            }
                        )
                        SdmTooltipIconButton(
                            icon = icon,
                            label = label,
                            onClick = onToggleDirectoryView,
                        )
                    },
                )
            } else {
                val clusterId = currentCluster?.identifier
                SdmSelectionTopAppBar(
                    selectedCount = selection.size,
                    onClearSelection = { selection = emptySet() },
                    actions = {
                        SdmDeleteAction(
                            enabled = clusterId != null,
                            onClick = {
                                if (clusterId != null) {
                                    val selectedIds = selection
                                    onDeleteSelectedDuplicates(clusterId, selectedIds)
                                }
                            },
                        )
                        SdmExcludeAction(
                            enabled = clusterId != null,
                            onClick = {
                                if (clusterId != null) {
                                    val selectedIds = selection
                                    selection = emptySet()
                                    onExcludeSelectedDuplicates(clusterId, selectedIds)
                                }
                            },
                        )
                        SdmSelectAllAction(
                            visible = selection.size < maxSelection,
                            onClick = { selection = liveDupeIds.take(maxSelection).toSet() },
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
                data = current?.progress,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (current == null) {
                    SdmLoadingState()
                } else if (items.isEmpty()) {
                    SdmEmptyState()
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SdmScrollableTabStrip(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, items.lastIndex),
                            tabCount = items.size,
                            onTabSelected = { index ->
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                        ) { index ->
                            Text(
                                text = stringResource(
                                    DeduplicatorR.string.deduplicator_cluster_x_label,
                                    "#${index + 1}",
                                )
                            )
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.Top,
                        ) { page ->
                            val cluster = items.getOrNull(page) ?: return@HorizontalPager
                            val collapsedForCluster = current?.collapsedDirs?.get(cluster.identifier) ?: emptySet()
                            val isTourCluster = cluster.identifier == tourClusterId
                            ClusterContent(
                                cluster = cluster,
                                isDirectoryView = current?.isDirectoryView == true,
                                collapsed = collapsedForCluster,
                                selection = if (cluster.identifier == currentCluster?.identifier) selection else emptySet(),
                                onSelectionToggle = { id ->
                                    if (cluster.identifier != currentCluster?.identifier) return@ClusterContent
                                    selection = if (selection.contains(id)) {
                                        selection - id
                                    } else if (selection.size < maxSelection) {
                                        selection + id
                                    } else {
                                        selection
                                    }
                                },
                                onSelectionLongPress = { id ->
                                    if (cluster.identifier != currentCluster?.identifier) return@ClusterContent
                                    if (selection.size < maxSelection) selection = selection + id
                                },
                                onCollapseToggle = { dirId -> onCollapseToggle(cluster.identifier, dirId) },
                                onClusterDelete = { onClusterDelete(cluster.identifier) },
                                onClusterExclude = { onClusterExclude(cluster.identifier) },
                                onGroupDelete = { groupId -> onGroupDelete(cluster.identifier, groupId) },
                                onGroupView = onGroupView,
                                onDuplicateDelete = { id -> onDuplicateDelete(cluster.identifier, id) },
                                onDuplicatePreview = onDuplicatePreview,
                                onDirectoryDeleteAll = { dirGroup -> onDirectoryDeleteAll(cluster.identifier, dirGroup) },
                                listState = if (isTourCluster) tourListState else rememberLazyListState(),
                                applyClusterHeaderTourTarget = isTourCluster,
                                tourDeleteMarkTarget = if (isTourCluster) tourDeleteMarkRowId else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun DeduplicatorDetailsScreenEmptyPreview() {
    PreviewWrapper {
        DeduplicatorDetailsScreen(
            stateSource = MutableStateFlow(
                DeduplicatorDetailsViewModel.State(
                    items = emptyList(),
                    target = null,
                    progress = null,
                )
            ),
        )
    }
}
