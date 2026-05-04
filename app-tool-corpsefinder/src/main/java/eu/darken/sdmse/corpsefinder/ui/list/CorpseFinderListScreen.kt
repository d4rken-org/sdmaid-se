package eu.darken.sdmse.corpsefinder.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.ui.list.items.CorpseRow
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun CorpseFinderListScreenHost(
    vm: CorpseFinderListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var confirmDeletion by remember { mutableStateOf<CorpseFinderListViewModel.Event.ConfirmDeletion?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is CorpseFinderListViewModel.Event.ConfirmDeletion -> confirmDeletion = event

                is CorpseFinderListViewModel.Event.ExclusionsCreated -> snackScope.launch {
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

                is CorpseFinderListViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
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
        val message = if (pending.isSingle) {
            val id = pending.ids.first()
            val row = vm.state.value.rows?.firstOrNull { it.identifier == id }
            val name = row?.corpse?.lookup?.userReadableName?.get(context) ?: id.toString()
            stringResource(CommonR.string.general_delete_confirmation_message_x, name)
        } else {
            pluralStringResource(
                CommonR.plurals.general_delete_confirmation_message_selected_x_items,
                pending.ids.size,
                pending.ids.size,
            )
        }

        AlertDialog(
            onDismissRequest = { confirmDeletion = null },
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = { Text(message) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { confirmDeletion = null }) {
                        Text(stringResource(CommonR.string.general_cancel_action))
                    }
                    if (pending.isSingle) {
                        TextButton(onClick = {
                            val ids = pending.ids
                            confirmDeletion = null
                            vm.onShowDetailsFromDialog(ids)
                        }) {
                            Text(stringResource(CommonR.string.general_show_details_action))
                        }
                    }
                    TextButton(onClick = {
                        val ids = pending.ids
                        confirmDeletion = null
                        vm.onDeleteConfirmed(ids)
                    }) {
                        Text(stringResource(CommonR.string.general_delete_action))
                    }
                }
            },
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
                )
            } else {
                SdmSelectionTopAppBar(
                    selectedCount = selection.size,
                    onClearSelection = { selection = emptySet() },
                    actions = {
                        SdmDeleteAction(onClick = {
                            val ids = selection
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
                                )
                            }
                        }
                    }
                }
            }
        }
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
