package eu.darken.sdmse.appcleaner.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.ui.list.items.AppCleanerListRow
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
import eu.darken.sdmse.common.compose.layout.SdmTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun AppCleanerListScreenHost(
    vm: AppCleanerListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var pendingDeletion by remember { mutableStateOf<AppCleanerListViewModel.Event.ConfirmDeletion?>(null) }

    val viewActionLabel = stringResource(CommonR.string.general_view_action)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AppCleanerListViewModel.Event.ConfirmDeletion -> pendingDeletion = event
                is AppCleanerListViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
                is AppCleanerListViewModel.Event.ExclusionsCreated -> snackScope.launch {
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

    AppCleanerListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onRowClick = vm::onRowClick,
        onDetailsClick = vm::onDetailsClick,
        onDeleteSelected = vm::onDeleteSelected,
        onExcludeSelected = vm::onExcludeSelected,
    )

    pendingDeletion?.let { pending ->
        val singleId = pending.ids.singleOrNull()
        val message = if (singleId != null) {
            val row = vm.state.value.rows?.firstOrNull { it.identifier == singleId }
            val name = row?.junk?.label?.get(context) ?: singleId.pkgId.name
            stringResource(R.string.appcleaner_delete_confirmation_message_x, name)
        } else {
            pluralStringResource(
                R.plurals.appcleaner_delete_confirmation_message_selected_x_items,
                pending.ids.size,
                pending.ids.size,
            )
        }

        SdmConfirmDialog(
            title = stringResource(CommonR.string.general_delete_confirmation_title),
            message = message,
            onDismissRequest = { pendingDeletion = null },
            positive = SdmDialogAction(
                label = stringResource(
                    if (singleId != null) CommonR.string.general_delete_action
                    else CommonR.string.general_delete_selected_action,
                ),
                onClick = {
                    val ids = pending.ids
                    pendingDeletion = null
                    vm.onDeleteConfirmed(ids)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { pendingDeletion = null },
            ),
            neutral = SdmDialogAction(
                label = stringResource(CommonR.string.general_show_details_action),
                onClick = {
                    val ids = pending.ids
                    pendingDeletion = null
                    vm.onShowDetailsFromDialog(ids)
                },
            ),
        )
    }
}

@Composable
internal fun AppCleanerListScreen(
    stateSource: StateFlow<AppCleanerListViewModel.State> =
        MutableStateFlow(AppCleanerListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onRowClick: (AppCleanerListViewModel.Row) -> Unit = {},
    onDetailsClick: (AppCleanerListViewModel.Row) -> Unit = {},
    onDeleteSelected: (Set<InstallId>) -> Unit = {},
    onExcludeSelected: (Set<InstallId>) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state.rows

    var selection by remember { mutableStateOf<Set<InstallId>>(emptySet()) }
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
                    title = stringResource(CommonR.string.appcleaner_tool_name),
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
                                AppCleanerListRow(
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
private fun AppCleanerListScreenLoadingPreview() {
    PreviewWrapper {
        AppCleanerListScreen(
            stateSource = MutableStateFlow(AppCleanerListViewModel.State(rows = null)),
        )
    }
}

@Preview2
@Composable
private fun AppCleanerListScreenEmptyPreview() {
    PreviewWrapper {
        AppCleanerListScreen(
            stateSource = MutableStateFlow(AppCleanerListViewModel.State(rows = emptyList())),
        )
    }
}
