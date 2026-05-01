package eu.darken.sdmse.exclusion.ui.list

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.exclusion.core.types.Exclusion
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
                    val result = snackbarHostState.showSnackbar(
                        message = "Removed ${event.exclusions.size} exclusions",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restore(event.exclusions)
                    }
                }
                is ExclusionListViewModel.Event.ImportSuccess -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Imported ${event.exclusions.size} exclusions",
                        duration = SnackbarDuration.Long,
                    )
                }
                is ExclusionListViewModel.Event.ExportSuccess -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Exported ${event.exclusions.size} exclusions",
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    ExclusionListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
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
        AlertDialog(
            onDismissRequest = { pendingTypePicker = false },
            title = { Text(stringResource(R.string.exclusion_create_action)) },
            text = {
                Column {
                    ListItem(
                        modifier = Modifier.clickableItem {
                            pendingTypePicker = false
                            vm.openAppControl()
                        },
                        headlineContent = { Text(stringResource(R.string.exclusion_type_package)) },
                        supportingContent = { Text(stringResource(R.string.exclusion_create_pkg_hint)) },
                    )
                    ListItem(
                        modifier = Modifier.clickableItem {
                            pendingTypePicker = false
                            vm.openStoragePicker()
                        },
                        headlineContent = { Text(stringResource(R.string.exclusion_type_path)) },
                        supportingContent = { Text(stringResource(R.string.exclusion_create_path_hint)) },
                    )
                    ListItem(
                        modifier = Modifier.clickableItem {
                            pendingTypePicker = false
                            vm.openSegmentEditor()
                        },
                        headlineContent = { Text(stringResource(R.string.exclusion_type_segment)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingTypePicker = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
internal fun ExclusionListScreen(
    stateSource: StateFlow<ExclusionListViewModel.State> = MutableStateFlow(ExclusionListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
    val selectableIds = rows?.filter { !it.isDefault }?.map { it.stableId }?.toSet() ?: emptySet()

    var selection by remember { mutableStateOf<Set<ExclusionId>>(emptySet()) }
    // Prune stale IDs when the underlying list changes.
    LaunchedEffect(currentIds) { selection = selection intersect currentIds }

    var overflowExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.exclusion_manager_title)) },
                    actions = {
                        IconButton(onClick = onImport) {
                            Icon(
                                imageVector = Icons.TwoTone.FileDownload,
                                contentDescription = stringResource(R.string.exclusion_import_action),
                            )
                        }
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.TwoTone.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            if (state.showDefaults) {
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
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                onExportSelected(selection.toSet())
                                selection = emptySet()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.FileUpload,
                                contentDescription = stringResource(R.string.exclusion_export_action),
                            )
                        }
                        IconButton(
                            onClick = {
                                onRemoveSelected(selection.toSet())
                                selection = emptySet()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_remove_action),
                            )
                        }
                        IconButton(onClick = { selection = selectableIds }) {
                            Icon(
                                imageVector = Icons.TwoTone.SelectAll,
                                contentDescription = stringResource(CommonR.string.general_list_select_all_action),
                            )
                        }
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
            when (rows) {
                null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "info-card") {
                        ExclusionInfoCard(onMoreInfo = onMoreInfo)
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty-hint") {
                            Text(
                                text = stringResource(R.string.exclusion_add_new_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 24.dp),
                            )
                        }
                    } else {
                        items(rows, key = { it.stableId }) { row ->
                            val isSelected = selection.contains(row.stableId)
                            val rowModifier = Modifier
                            when (row) {
                                is ExclusionListViewModel.Row.Pkg -> PkgExclusionRow(
                                    modifier = rowModifier,
                                    row = row,
                                    selected = isSelected,
                                    selectionActive = selection.isNotEmpty(),
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = selection.toggle(row.stableId)
                                        } else {
                                            onRowClick(row)
                                        }
                                    },
                                    onLongClick = {
                                        if (!row.isDefault) selection = selection.toggle(row.stableId)
                                    },
                                )

                                is ExclusionListViewModel.Row.Path -> PathExclusionRow(
                                    modifier = rowModifier,
                                    row = row,
                                    selected = isSelected,
                                    selectionActive = selection.isNotEmpty(),
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = selection.toggle(row.stableId)
                                        } else {
                                            onRowClick(row)
                                        }
                                    },
                                    onLongClick = {
                                        if (!row.isDefault) selection = selection.toggle(row.stableId)
                                    },
                                )

                                is ExclusionListViewModel.Row.Segment -> SegmentExclusionRow(
                                    modifier = rowModifier,
                                    row = row,
                                    selected = isSelected,
                                    selectionActive = selection.isNotEmpty(),
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = selection.toggle(row.stableId)
                                        } else {
                                            onRowClick(row)
                                        }
                                    },
                                    onLongClick = {
                                        if (!row.isDefault) selection = selection.toggle(row.stableId)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExclusionInfoCard(
    onMoreInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.exclusion_explanation_body1),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                onClick = onMoreInfo,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(CommonR.string.general_more_info_action))
            }
        }
    }
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
