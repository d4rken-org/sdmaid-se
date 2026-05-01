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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
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
                        } catch (_: ActivityNotFoundException) {
                            // Swallow — matches existing Fragment's asErrorDialogBuilder behavior
                            // (user already has a snackbar, dialog would stack on top).
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
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedRows = state.rows.filter { selection.contains(it.id) }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.systemcleaner_customfilter_label)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = onImport) {
                            Icon(
                                Icons.TwoTone.FileUpload,
                                contentDescription = stringResource(R.string.systemcleaner_customfilter_import_action),
                            )
                        }
                        IconButton(onClick = onHelp) {
                            Icon(Icons.TwoTone.HelpOutline, contentDescription = null)
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
                        if (selection.size == 1) {
                            IconButton(onClick = {
                                val row = selectedRows.first()
                                selection = emptySet()
                                onEditRow(row)
                            }) {
                                Icon(Icons.TwoTone.Edit, contentDescription = null)
                            }
                        }
                        IconButton(onClick = {
                            val rows = selectedRows.toList()
                            selection = emptySet()
                            onExportSelected(rows)
                        }) {
                            Icon(
                                Icons.TwoTone.FileDownload,
                                contentDescription = stringResource(R.string.systemcleaner_customfilter_export_action),
                            )
                        }
                        IconButton(onClick = {
                            val rows = selectedRows.toList()
                            selection = emptySet()
                            onRemoveSelected(rows)
                        }) {
                            Icon(Icons.TwoTone.Delete, contentDescription = null)
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selection.isEmpty() && state.isPro != null) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.systemcleaner_customfilter_label)) },
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
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.rows.isEmpty() -> EmptyStateBody()

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        val isSelected = selection.contains(row.id)
                        CustomFilterRow(
                            row = row,
                            selected = isSelected,
                            selectionActive = selection.isNotEmpty(),
                            onClick = {
                                if (selection.isNotEmpty()) {
                                    selection = if (isSelected) selection - row.id else selection + row.id
                                } else {
                                    onToggleRow(row)
                                }
                            },
                            onLongClick = {
                                selection = selection + row.id
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
