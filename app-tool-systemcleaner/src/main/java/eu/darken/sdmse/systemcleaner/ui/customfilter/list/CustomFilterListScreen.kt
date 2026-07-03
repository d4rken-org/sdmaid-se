package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.ScrollAwareFab
import eu.darken.sdmse.common.compose.layout.SdmListDefaults
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.selection.rememberSelectionState
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.systemcleaner.R
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.ui.customfilter.list.items.CustomFilterRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun CustomFilterListScreenHost(
    vm: CustomFilterListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uris = buildList {
            val clip = result.data?.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
            } else {
                result.data?.data?.let { add(it) }
            }
        }
        if (uris.isNotEmpty()) vm.importFilter(uris)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.data?.let { vm.performExport(it) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is CustomFilterListViewModel.Event.LaunchImport -> importLauncher.launch(event.intent)
                is CustomFilterListViewModel.Event.LaunchExport -> exportLauncher.launch(event.intent)
                is CustomFilterListViewModel.Event.UndoRemove -> snackScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            CommonR.plurals.general_remove_success_x_items,
                            event.configs.size,
                            event.configs.size,
                        ),
                        actionLabel = context.getString(CommonR.string.general_undo_action),
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restore(event.configs)
                }

                is CustomFilterListViewModel.Event.ExportFinished -> snackScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            CommonR.plurals.result_x_successful,
                            event.files.size,
                            event.files.size,
                        ),
                        actionLabel = context.getString(CommonR.string.general_view_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(event.path.uri, event.path.type)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // Surface via the error dialog (legacy used asErrorDialogBuilder) — no
                            // app to view the exported file should not silently do nothing.
                            vm.errorEvents.tryEmit(e)
                        }
                    }
                }
            }
        }
    }

    CustomFilterListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onToggleRow = vm::onToggleRow,
        onEditRow = vm::onEditClick,
        onCreate = vm::onCreateClick,
        onImport = vm::onImportClick,
        onHelp = vm::onHelpClick,
        onRemoveSelected = vm::removeRows,
        onExportSelected = vm::exportRows,
    )
}

@Composable
internal fun CustomFilterListScreen(
    stateSource: StateFlow<CustomFilterListViewModel.State> =
        MutableStateFlow(CustomFilterListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onToggleRow: (CustomFilterListViewModel.FilterRow) -> Unit = {},
    onEditRow: (CustomFilterListViewModel.FilterRow) -> Unit = {},
    onCreate: () -> Unit = {},
    onImport: () -> Unit = {},
    onHelp: () -> Unit = {},
    onRemoveSelected: (List<CustomFilterListViewModel.FilterRow>) -> Unit = {},
    onExportSelected: (List<CustomFilterListViewModel.FilterRow>) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val selection = rememberSelectionState<String>()
    val listState = rememberLazyListState()

    // Standard selection prune (same as the other tool lists): ids whose rows disappeared —
    // removal on another screen, an import replacing filters — must not linger in the count/title
    // or the next action's input.
    val rowIds = state?.rows?.map { it.id }?.toSet()
    LaunchedEffect(rowIds) {
        if (rowIds != null) selection.retainAll(rowIds)
    }

    BackHandler(enabled = selection.isActive) { selection.clear() }

    SdmScaffold(
        topBar = {
            if (!selection.isActive) {
                TopAppBar(
                    title = { Text(stringResource(R.string.systemcleaner_customfilter_label)) },
                    navigationIcon = {
                        SdmTooltipIconButton(
                            icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                            label = stringResource(CommonR.string.general_navigate_up_action),
                            onClick = onNavigateUp,
                        )
                    },
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.FileUpload,
                            label = stringResource(R.string.systemcleaner_customfilter_import_action),
                            onClick = onImport,
                        )
                        SdmTooltipIconButton(
                            icon = Icons.AutoMirrored.TwoTone.HelpOutline,
                            label = stringResource(CommonR.string.general_help_action),
                            onClick = onHelp,
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.count}") },
                    navigationIcon = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Close,
                            label = stringResource(CommonR.string.general_close_action),
                            onClick = { selection.clear() },
                        )
                    },
                    actions = {
                        if (selection.count == 1) {
                            SdmTooltipIconButton(
                                icon = Icons.TwoTone.Edit,
                                label = stringResource(CommonR.string.general_edit_action),
                                onClick = {
                                    val ids = selection.selected
                                    val row = state.rows.firstOrNull { it.id in ids } ?: return@SdmTooltipIconButton
                                    selection.clear()
                                    onEditRow(row)
                                },
                            )
                        }
                        if (selection.count < state.rows.size) {
                            SdmTooltipIconButton(
                                icon = Icons.TwoTone.SelectAll,
                                label = stringResource(CommonR.string.general_list_select_all_action),
                                onClick = { selection.setSelection(state.rows.map { it.id }.toSet()) },
                            )
                        }
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.FileDownload,
                            label = stringResource(R.string.systemcleaner_customfilter_export_action),
                            onClick = {
                                val ids = selection.selected
                                val rows = state.rows.filter { it.id in ids }
                                selection.clear()
                                onExportSelected(rows)
                            },
                        )
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Delete,
                            label = stringResource(CommonR.string.general_delete_action),
                            onClick = {
                                val ids = selection.selected
                                val rows = state.rows.filter { it.id in ids }
                                selection.clear()
                                onRemoveSelected(rows)
                            },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selection.isActive && state.isPro != null) {
                ScrollAwareFab(scrollState = listState, scrollHideEnabled = state.rows.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = onCreate,
                        icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.systemcleaner_customfilter_label)) },
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
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.rows.isEmpty() -> EmptyStateBody()

                else -> {
                    val selectionActive = selection.isActive
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = SdmListDefaults.FabClearance),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            val isSelected = selection.isSelected(row.id)
                            CustomFilterRow(
                                row = row,
                                selected = isSelected,
                                selectionActive = selectionActive,
                                onClick = {
                                    if (selection.isActive) {
                                        selection.toggle(row.id)
                                    } else {
                                        onToggleRow(row)
                                    }
                                },
                                onLongClick = {
                                    selection.select(row.id)
                                },
                                onEditClick = { onEditRow(row) },
                                onToggle = { onToggleRow(row) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBody() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.systemcleaner_customfilter_create_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview2
@Composable
private fun CustomFilterListScreenEmptyPreview() {
    PreviewWrapper {
        CustomFilterListScreen(
            stateSource = MutableStateFlow(
                CustomFilterListViewModel.State(rows = emptyList(), loading = false, isPro = true),
            ),
        )
    }
}

@Preview2
@Composable
private fun CustomFilterListScreenPopulatedPreview() {
    PreviewWrapper {
        CustomFilterListScreen(
            stateSource = MutableStateFlow(
                CustomFilterListViewModel.State(
                    rows = listOf(
                        CustomFilterListViewModel.FilterRow(
                            config = CustomFilterConfig(
                                identifier = "abc",
                                label = "Old downloads",
                                createdAt = Instant.now(),
                                modifiedAt = Instant.now(),
                            ),
                            isEnabled = true,
                        ),
                        CustomFilterListViewModel.FilterRow(
                            config = CustomFilterConfig(
                                identifier = "def",
                                label = "Receipt PDFs",
                                createdAt = Instant.now(),
                                modifiedAt = Instant.now().minusSeconds(86400),
                            ),
                            isEnabled = false,
                        ),
                    ),
                    loading = false,
                    isPro = true,
                ),
            ),
        )
    }
}
