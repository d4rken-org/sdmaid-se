package eu.darken.sdmse.corpsefinder.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
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
import eu.darken.sdmse.corpsefinder.R
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.ui.list.items.CorpseRow
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CorpseFinderListScreenHost(
    vm: CorpseFinderListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmDeletion by remember { mutableStateOf<CorpseFinderListViewModel.Event.ConfirmDeletion?>(null) }

    ToolListEventHandler(
        events = vm.events,
        snackbarHostState = snackbarHostState,
        onShowExclusions = { vm.navTo(ExclusionsListRoute) },
    ) { event ->
        if (event is CorpseFinderListViewModel.Event.ConfirmDeletion) confirmDeletion = event
    }

    CorpseFinderListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onRowClick = vm::onRowClick,
        onDetailsClick = vm::onDetailsClick,
        onDeleteSelected = vm::onDeleteSelected,
        onExcludeSelected = vm::onExcludeSelected,
    )

    confirmDeletion?.let { pending ->
        val singleId = pending.ids.singleOrNull()
        val message = if (singleId != null) {
            val row = vm.state.value.rows?.firstOrNull { it.identifier == singleId }
            val name = row?.corpse?.lookup?.userReadableName?.get(context) ?: singleId.toString()
            stringResource(CommonR.string.general_delete_confirmation_message_x, name)
        } else {
            pluralStringResource(
                CommonR.plurals.general_delete_confirmation_message_selected_x_items,
                pending.ids.size,
                pending.ids.size,
            )
        }

        SdmConfirmDialog(
            title = stringResource(CommonR.string.general_delete_confirmation_title),
            message = message,
            onDismissRequest = { confirmDeletion = null },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_delete_action),
                onClick = {
                    val ids = pending.ids
                    confirmDeletion = null
                    vm.onDeleteConfirmed(ids)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { confirmDeletion = null },
            ),
            // "Show details" only makes sense for a single corpse — onShowDetailsFromDialog opens
            // just the first id, so showing it for multi-select would silently drop the rest.
            neutral = if (pending.ids.size == 1) SdmDialogAction(
                label = stringResource(CommonR.string.general_show_details_action),
                onClick = {
                    val ids = pending.ids
                    confirmDeletion = null
                    vm.onShowDetailsFromDialog(ids)
                },
            ) else null,
        )
    }
}

@Composable
internal fun CorpseFinderListScreen(
    stateSource: StateFlow<CorpseFinderListViewModel.State> = MutableStateFlow(CorpseFinderListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onRowClick: (CorpseFinderListViewModel.Row) -> Unit = {},
    onDetailsClick: (CorpseFinderListViewModel.Row) -> Unit = {},
    onDeleteSelected: (Set<CorpseIdentifier>) -> Unit = {},
    onExcludeSelected: (Set<CorpseIdentifier>) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state.rows

    var selection by remember { mutableStateOf<Set<CorpseIdentifier>>(emptySet()) }
    var showMarkersInfo by remember { mutableStateOf(false) }
    val rowIds = rows?.map { it.identifier }?.toSet() ?: emptySet()
    LaunchedEffect(rowIds) {
        selection = selection intersect rowIds
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val subtitle = rows?.let { list ->
        if (state.progress == null) {
            pluralStringResource(CommonR.plurals.result_x_items, list.size, list.size)
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                SdmTopAppBar(
                    title = stringResource(CommonR.string.corpsefinder_tool_name),
                    subtitle = subtitle,
                    onNavigateUp = onNavigateUp,
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Info,
                            label = stringResource(R.string.corpsefinder_markers_dialog_title),
                            onClick = { showMarkersInfo = true },
                        )
                    },
                )
            } else {
                SdmSelectionTopAppBar(
                    selectedCount = selection.size,
                    onClearSelection = { selection = emptySet() },
                    actions = {
                        SdmDeleteAction(onClick = {
                            val ids = selection
                            selection = emptySet()
                            onDeleteSelected(ids)
                        })
                        SdmExcludeAction(onClick = {
                            val ids = selection
                            selection = emptySet()
                            onExcludeSelected(ids)
                        })
                        SdmSelectAllAction(
                            visible = selection.size < rowIds.size,
                            onClick = { selection = rowIds },
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
                data = state.progress,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    rows == null -> SdmLoadingState()

                    rows.isEmpty() -> SdmEmptyState()

                    else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val context = LocalContext.current
                        val spanCount = remember(maxWidth) {
                            context.getSpanCount(widthDp = SdmListDefaults.ToolGridMinWidth.value.toInt())
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = SdmListDefaults.GridContentPadding,
                        ) {
                            items(rows, key = { it.identifier.toString() }) { row ->
                                val isSelected = selection.contains(row.identifier)
                                CorpseRow(
                                    row = row,
                                    selected = isSelected,
                                    selectionActive = selection.isNotEmpty(),
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = if (isSelected) {
                                                selection - row.identifier
                                            } else {
                                                selection + row.identifier
                                            }
                                        } else {
                                            onRowClick(row)
                                        }
                                    },
                                    onLongClick = {
                                        selection = selection + row.identifier
                                    },
                                    onDetailsClick = { onDetailsClick(row) },
                                    onRiskChipClick = { showMarkersInfo = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMarkersInfo) {
        CorpseFinderMarkersDialog(onDismiss = { showMarkersInfo = false })
    }
}

@Preview2
@Composable
private fun CorpseFinderListScreenLoadingPreview() {
    PreviewWrapper {
        CorpseFinderListScreen(
            stateSource = MutableStateFlow(CorpseFinderListViewModel.State(rows = null)),
        )
    }
}

@Preview2
@Composable
private fun CorpseFinderListScreenEmptyPreview() {
    PreviewWrapper {
        CorpseFinderListScreen(
            stateSource = MutableStateFlow(CorpseFinderListViewModel.State(rows = emptyList())),
        )
    }
}
