package eu.darken.sdmse.appcleaner.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import eu.darken.sdmse.common.compose.layout.SdmSearchBar
import eu.darken.sdmse.common.compose.layout.SdmSelectAllAction
import eu.darken.sdmse.common.compose.layout.SdmSelectionTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.snackbar.ToolListEventHandler
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppCleanerListScreenHost(
    vm: AppCleanerListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDeletion by remember { mutableStateOf<AppCleanerListViewModel.Event.ConfirmDeletion?>(null) }

    ToolListEventHandler(
        events = vm.events,
        snackbarHostState = snackbarHostState,
        onShowExclusions = vm::onShowExclusions,
    ) { event ->
        if (event is AppCleanerListViewModel.Event.ConfirmDeletion) pendingDeletion = event
    }

    AppCleanerListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onRowClick = vm::onRowClick,
        onDetailsClick = vm::onDetailsClick,
        onDeleteSelected = vm::onDeleteSelected,
        onExcludeSelected = vm::onExcludeSelected,
        onSearchQueryChanged = vm::onSearchQueryChanged,
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
    onSearchQueryChanged: (String) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state.rows

    var selection by remember { mutableStateOf<Set<InstallId>>(emptySet()) }
    val rowIds = rows?.map { it.identifier }?.toSet() ?: emptySet()
    LaunchedEffect(rowIds) {
        selection = selection intersect rowIds
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    var searchActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.isSearchFilterActive) {
        if (state.isSearchFilterActive && !searchActive) searchActive = true
    }
    BackHandler(enabled = searchActive && selection.isEmpty()) {
        onSearchQueryChanged("")
        searchActive = false
    }
    // The IME's window-level back handler grabs the first back press while the search field has
    // focus, which prevents the BackHandler above from firing. Watch the IME insets and treat the
    // first IME-hide after the field opened (and got the IME up) as a request to collapse search,
    // so a single back press both hides the keyboard and closes the search bar.
    val isImeVisible = WindowInsets.isImeVisible
    var searchImeWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(searchActive, isImeVisible) {
        if (!searchActive) {
            searchImeWasShown = false
            return@LaunchedEffect
        }
        if (isImeVisible) {
            searchImeWasShown = true
        } else if (searchImeWasShown && selection.isEmpty()) {
            onSearchQueryChanged("")
            searchActive = false
        }
    }

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
                TopAppBar(
                    title = {
                        if (searchActive) {
                            SdmSearchBar(
                                query = state.searchQuery,
                                onQueryChange = onSearchQueryChanged,
                                onClose = {
                                    onSearchQueryChanged("")
                                    searchActive = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column {
                                Text(stringResource(CommonR.string.appcleaner_tool_name))
                                if (subtitle != null) {
                                    Text(subtitle, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                                contentDescription = stringResource(CommonR.string.general_navigate_up_action),
                            )
                        }
                    },
                    actions = {
                        if (!searchActive) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(
                                    imageVector = Icons.TwoTone.Search,
                                    contentDescription = stringResource(CommonR.string.general_search_action),
                                )
                            }
                        }
                    },
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

                    rows.isEmpty() -> if (state.isSearchFilterActive && state.totalCount > 0) {
                        SdmEmptyState(title = stringResource(CommonR.string.general_search_no_matches))
                    } else {
                        SdmEmptyState()
                    }

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

@Preview2
@Composable
private fun AppCleanerListScreenNoMatchesPreview() {
    PreviewWrapper {
        AppCleanerListScreen(
            stateSource = MutableStateFlow(
                AppCleanerListViewModel.State(
                    rows = emptyList(),
                    searchQuery = "xyz",
                    isSearchFilterActive = true,
                    totalCount = 12,
                ),
            ),
        )
    }
}
