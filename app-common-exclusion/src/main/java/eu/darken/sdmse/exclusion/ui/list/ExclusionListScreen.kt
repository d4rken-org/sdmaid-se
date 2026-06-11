package eu.darken.sdmse.exclusion.ui.list

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Shield
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SdmModalBottomSheet
import eu.darken.sdmse.common.compose.layout.SdmDeleteAction
import eu.darken.sdmse.common.compose.layout.SdmEmptyState
import eu.darken.sdmse.common.compose.layout.SdmLoadingState
import eu.darken.sdmse.common.compose.layout.SdmSelectAllAction
import eu.darken.sdmse.common.compose.layout.SdmSelectionTopAppBar
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.layout.SdmTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.ui.list.items.PathExclusionRow
import eu.darken.sdmse.exclusion.ui.list.items.PkgExclusionRow
import eu.darken.sdmse.exclusion.ui.list.items.SegmentExclusionRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun ExclusionListScreenHost(
    vm: ExclusionListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uris = mutableListOf<Uri>()
        val clipData = result.data?.clipData
        if (clipData != null) {
            (0 until clipData.itemCount).forEach { uris.add(clipData.getItemAt(it).uri) }
        } else {
            result.data?.data?.let { uris.add(it) }
        }
        vm.importExclusions(uris)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.data?.let { vm.performExport(it) }
    }

    var pendingTypePicker by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ExclusionListViewModel.Event.ImportEvent -> importLauncher.launch(event.intent)
                is ExclusionListViewModel.Event.ExportEvent -> exportLauncher.launch(event.intent)
                is ExclusionListViewModel.Event.UndoRemove -> scope.launch {
                    val count = event.exclusions.size
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            CommonR.plurals.general_remove_success_x_items,
                            count,
                            count,
                        ),
                        actionLabel = context.getString(CommonR.string.general_undo_action),
                        // Indefinite (legacy used LENGTH_INDEFINITE): give unlimited time to undo a removal.
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restore(event.exclusions)
                    }
                }
                is ExclusionListViewModel.Event.ImportSuccess -> scope.launch {
                    val count = event.exclusions.size
                    snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            R.plurals.exclusion_import_success_x_items,
                            count,
                            count,
                        ),
                        duration = SnackbarDuration.Long,
                    )
                }
                is ExclusionListViewModel.Event.ExportSuccess -> scope.launch {
                    val count = event.exclusions.size
                    snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            R.plurals.exclusion_export_success_x_items,
                            count,
                            count,
                        ),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    ExclusionListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onRowClick = vm::onRowClick,
        onMoreInfo = vm::openHelp,
        onImport = vm::requestImport,
        onResetDefaults = vm::resetDefaultExclusions,
        onToggleShowDefaults = vm::showDefaultExclusions,
        onRemoveSelected = { vm.removeByIds(it) },
        onExportSelected = { vm.exportExclusions(it) },
        onAddExclusion = { pendingTypePicker = true },
    )

    if (pendingTypePicker) {
        SdmModalBottomSheet(onDismiss = { pendingTypePicker = false }) { dismiss ->
            CreateExclusionTypeSheet(
                onPickPackage = { dismiss { vm.openAppControl() } },
                onPickPath = { dismiss { vm.openStoragePicker() } },
                onPickSegment = { dismiss { vm.openSegmentEditor() } },
            )
        }
    }
}

@Composable
internal fun ExclusionListScreen(
    stateSource: StateFlow<ExclusionListViewModel.State> = MutableStateFlow(ExclusionListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onRowClick: (ExclusionListViewModel.Row) -> Unit = {},
    onMoreInfo: () -> Unit = {},
    onImport: () -> Unit = {},
    onResetDefaults: () -> Unit = {},
    onToggleShowDefaults: (Boolean) -> Unit = {},
    onRemoveSelected: (Set<ExclusionId>) -> Unit = {},
    onExportSelected: (Set<ExclusionId>) -> Unit = {},
    onAddExclusion: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state.rows
    val currentIds = rows?.map { it.stableId }?.toSet() ?: emptySet()
    // Defaults are selectable/deletable too (legacy parity): removal persists, and they can be brought
    // back via the Undo snackbar or "Reset defaults". So "Select all" covers every row.
    val selectableIds = currentIds

    var selection by remember { mutableStateOf<Set<ExclusionId>>(emptySet()) }
    // Prune stale IDs when the underlying list changes.
    LaunchedEffect(currentIds) { selection = selection intersect currentIds }

    var overflowExpanded by remember { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    SdmScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selection.isEmpty()) {
                SdmTopAppBar(
                    title = stringResource(R.string.exclusion_manager_title),
                    onNavigateUp = onNavigateUp,
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Info,
                            label = stringResource(CommonR.string.general_info_label),
                            onClick = { infoOpen = true },
                        )
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.FileDownload,
                            label = stringResource(R.string.exclusion_import_action),
                            onClick = onImport,
                        )
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.MoreVert,
                            label = stringResource(CommonR.string.general_options_label),
                            onClick = { overflowExpanded = true },
                        )
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            // Only shown when the built-in defaults have actually been modified (a
                            // default removed or shadowed by a user exclusion); otherwise restoring
                            // is a no-op. Independent of the "Show defaults" toggle below.
                            if (state.defaultsModified) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.exclusion_reset_default_exclusions)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onResetDefaults()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.exclusion_show_defaults_action)) },
                                onClick = {
                                    overflowExpanded = false
                                    onToggleShowDefaults(!state.showDefaults)
                                },
                                trailingIcon = {
                                    if (state.showDefaults) {
                                        Icon(
                                            imageVector = Icons.TwoTone.Check,
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            } else {
                SdmSelectionTopAppBar(
                    selectedCount = selection.size,
                    onClearSelection = { selection = emptySet() },
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.FileUpload,
                            label = stringResource(R.string.exclusion_export_action),
                            onClick = {
                                onExportSelected(selection.toSet())
                                selection = emptySet()
                            },
                        )
                        SdmDeleteAction(onClick = {
                            onRemoveSelected(selection.toSet())
                            selection = emptySet()
                        })
                        SdmSelectAllAction(
                            visible = selection.size < selectableIds.size,
                            onClick = { selection = selectableIds },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (selection.isEmpty()) {
                FloatingActionButton(onClick = onAddExclusion) {
                    Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.exclusion_create_action))
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                rows == null -> SdmLoadingState()

                rows.isEmpty() -> ExclusionEmptyState(
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.stableId }) { row ->
                        val isSelected = selection.contains(row.stableId)
                        val onRowTap = {
                            if (selection.isNotEmpty()) {
                                selection = selection.toggle(row.stableId)
                            } else {
                                onRowClick(row)
                            }
                        }
                        val onRowLongPress = {
                            selection = selection.toggle(row.stableId)
                        }
                        when (row) {
                            is ExclusionListViewModel.Row.Pkg -> PkgExclusionRow(
                                row = row,
                                selected = isSelected,
                                selectionActive = selection.isNotEmpty(),
                                onClick = onRowTap,
                                onLongClick = onRowLongPress,
                            )

                            is ExclusionListViewModel.Row.Path -> PathExclusionRow(
                                row = row,
                                selected = isSelected,
                                selectionActive = selection.isNotEmpty(),
                                onClick = onRowTap,
                                onLongClick = onRowLongPress,
                            )

                            is ExclusionListViewModel.Row.Segment -> SegmentExclusionRow(
                                row = row,
                                selected = isSelected,
                                selectionActive = selection.isNotEmpty(),
                                onClick = onRowTap,
                                onLongClick = onRowLongPress,
                            )
                        }
                    }
                }
            }
        }
    }

    if (infoOpen) {
        ExclusionInfoDialog(
            onMoreInfo = {
                infoOpen = false
                onMoreInfo()
            },
            onDismiss = { infoOpen = false },
        )
    }
}

@Composable
private fun ExclusionInfoDialog(
    onMoreInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    SdmAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.exclusion_manager_title)) },
        text = { Text(stringResource(R.string.exclusion_explanation_body1)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_close_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onMoreInfo) {
                Text(stringResource(CommonR.string.general_more_info_action))
            }
        },
    )
}

@Composable
private fun ExclusionEmptyState(
    modifier: Modifier = Modifier,
) {
    SdmEmptyState(
        modifier = modifier,
        title = stringResource(R.string.exclusion_add_new_hint),
        visual = {
            Icon(
                imageVector = Icons.TwoTone.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp),
            )
        },
    )
}

private fun Set<ExclusionId>.toggle(id: ExclusionId): Set<ExclusionId> =
    if (contains(id)) this - id else this + id

@Preview2
@Composable
private fun ExclusionListScreenEmptyPreview() {
    PreviewWrapper {
        ExclusionListScreen(
            stateSource = MutableStateFlow(
                ExclusionListViewModel.State(rows = emptyList()),
            ),
        )
    }
}

@Preview2
@Composable
private fun ExclusionListScreenLoadingPreview() {
    PreviewWrapper {
        ExclusionListScreen(
            stateSource = MutableStateFlow(
                ExclusionListViewModel.State(rows = null),
            ),
        )
    }
}
