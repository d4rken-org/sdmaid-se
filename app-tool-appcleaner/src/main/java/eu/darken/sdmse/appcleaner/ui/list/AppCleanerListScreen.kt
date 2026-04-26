package eu.darken.sdmse.appcleaner.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.ui.list.items.AppCleanerListRow
import eu.darken.sdmse.common.R as CommonR
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
        val message = if (pending.isSingle) {
            val id = pending.ids.first()
            val row = vm.state.value.rows?.firstOrNull { it.identifier == id }
            val name = row?.junk?.label?.get(context) ?: id.pkgId.name
            stringResource(R.string.appcleaner_delete_confirmation_message_x, name)
        } else {
            pluralStringResource(
                R.plurals.appcleaner_delete_confirmation_message_selected_x_items,
                pending.ids.size,
                pending.ids.size,
            )
        }

        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = { Text(message) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pendingDeletion = null }) {
                        Text(stringResource(CommonR.string.general_cancel_action))
                    }
                    if (pending.isSingle) {
                        TextButton(onClick = {
                            val ids = pending.ids
                            pendingDeletion = null
                            vm.onShowDetailsFromDialog(ids)
                        }) {
                            Text(stringResource(CommonR.string.general_show_details_action))
                        }
                    }
                    TextButton(onClick = {
                        val ids = pending.ids
                        pendingDeletion = null
                        vm.onDeleteConfirmed(ids)
                    }) {
                        Text(
                            stringResource(
                                if (pending.isSingle) CommonR.string.general_delete_action
                                else CommonR.string.general_delete_selected_action,
                            ),
                        )
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(CommonR.string.appcleaner_tool_name))
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val ids = selection
                            onDeleteSelected(ids)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_selected_action),
                            )
                        }
                        IconButton(onClick = {
                            val ids = selection
                            selection = emptySet()
                            onExcludeSelected(ids)
                        }) {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                        IconButton(onClick = { selection = rowIds }) {
                            Icon(
                                Icons.Filled.SelectAll,
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
                data = state.progress,
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

                    else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val context = LocalContext.current
                        val spanCount = remember(maxWidth) {
                            context.getSpanCount(widthDp = 410)
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
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
