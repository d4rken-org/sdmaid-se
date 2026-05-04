package eu.darken.sdmse.appcontrol.ui.list

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.appcontrol.ui.list.items.AppControlListRow
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun AppControlListScreenHost(
    vm: AppControlListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var pendingExportIds by remember { mutableStateOf<Set<InstallId>>(emptySet()) }
    var pendingConfirm by remember { mutableStateOf<AppControlListViewModel.Event?>(null) }
    var sizeSortCaveatVisible by rememberSaveable { mutableStateOf(false) }

    val viewActionLabel = stringResource(CommonR.string.general_view_action)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val ids = pendingExportIds
        pendingExportIds = emptySet()
        if (ids.isNotEmpty()) vm.onExportPathPicked(ids, result.data?.data)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AppControlListViewModel.Event.ConfirmToggle,
                is AppControlListViewModel.Event.ConfirmUninstall,
                is AppControlListViewModel.Event.ConfirmForceStop,
                is AppControlListViewModel.Event.ConfirmArchive,
                is AppControlListViewModel.Event.ConfirmRestore -> pendingConfirm = event

                is AppControlListViewModel.Event.ExclusionsCreated -> snackScope.launch {
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
                        vm.onShowExclusionsList()
                    }
                }

                is AppControlListViewModel.Event.ExportSelectPath -> {
                    pendingExportIds = event.ids
                    runCatching { exportLauncher.launch(event.intent) }
                        .onFailure { pendingExportIds = emptySet() }
                }

                is AppControlListViewModel.Event.ShowResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Short,
                    )
                }

                is AppControlListViewModel.Event.ShareList -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                }

                AppControlListViewModel.Event.ShowSizeSortCaveat -> sizeSortCaveatVisible = true
            }
        }
    }

    AppControlListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        sizeSortCaveatVisible = sizeSortCaveatVisible,
        onNavigateUp = vm::navUp,
        onTapRow = vm::onTapRow,
        onSearchQueryChanged = vm::onSearchQueryChanged,
        onSortModeChanged = vm::onSortModeChanged,
        onSortDirectionToggle = vm::onSortDirectionToggle,
        onTagToggle = vm::onTagToggle,
        onTagsReset = vm::onTagsReset,
        onSizeSortCaveatAck = {
            sizeSortCaveatVisible = false
            vm.onAckSizeSortCaveat()
        },
        onExcludeSelected = vm::onExcludeSelected,
        onToggleSelected = vm::onToggleRequested,
        onUninstallSelected = vm::onUninstallRequested,
        onForceStopSelected = vm::onForceStopRequested,
        onArchiveSelected = vm::onArchiveRequested,
        onRestoreSelected = vm::onRestoreRequested,
        onExportSelected = vm::onExportRequested,
        onShareSelected = vm::onShareList,
    )

    pendingConfirm?.let { ev ->
        when (ev) {
            is AppControlListViewModel.Event.ConfirmToggle -> ConfirmDialog(
                title = stringResource(R.string.appcontrol_toggle_confirmation_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_toggle_confirmation_message_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                confirmLabel = stringResource(CommonR.string.general_continue),
                onCancel = { pendingConfirm = null },
                onConfirm = {
                    val ids = ev.ids
                    pendingConfirm = null
                    vm.onToggleConfirmed(ids)
                },
            )

            is AppControlListViewModel.Event.ConfirmUninstall -> ConfirmDialog(
                title = stringResource(CommonR.string.general_delete_confirmation_title),
                message = if (ev.ids.size > 1) {
                    pluralStringResource(
                        CommonR.plurals.general_delete_confirmation_message_selected_x_items,
                        ev.ids.size,
                        ev.ids.size,
                    )
                } else {
                    val singleId = ev.ids.first()
                    val row = vm.state.value.rows?.firstOrNull { it.installId == singleId }
                    val name = row?.appInfo?.label?.get(context) ?: singleId.pkgId.name
                    stringResource(CommonR.string.general_delete_confirmation_message_x, name)
                },
                confirmLabel = stringResource(
                    if (ev.ids.size > 1) CommonR.string.general_delete_selected_action
                    else CommonR.string.general_delete_action,
                ),
                onCancel = { pendingConfirm = null },
                onConfirm = {
                    val ids = ev.ids
                    pendingConfirm = null
                    vm.onUninstallConfirmed(ids)
                },
            )

            is AppControlListViewModel.Event.ConfirmForceStop -> ConfirmDialog(
                title = stringResource(R.string.appcontrol_force_stop_confirm_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_force_stop_confirmation_message_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                confirmLabel = stringResource(R.string.appcontrol_force_stop_action),
                onCancel = { pendingConfirm = null },
                onConfirm = {
                    val ids = ev.ids
                    pendingConfirm = null
                    vm.onForceStopConfirmed(ids)
                },
            )

            is AppControlListViewModel.Event.ConfirmArchive -> ConfirmDialog(
                title = stringResource(R.string.appcontrol_archive_confirmation_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_archive_confirmation_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                confirmLabel = stringResource(R.string.appcontrol_archive_action),
                onCancel = { pendingConfirm = null },
                onConfirm = {
                    val ids = ev.ids
                    pendingConfirm = null
                    vm.onArchiveConfirmed(ids)
                },
            )

            is AppControlListViewModel.Event.ConfirmRestore -> ConfirmDialog(
                title = stringResource(R.string.appcontrol_restore_confirmation_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_restore_confirmation_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                confirmLabel = stringResource(R.string.appcontrol_restore_action),
                onCancel = { pendingConfirm = null },
                onConfirm = {
                    val ids = ev.ids
                    pendingConfirm = null
                    vm.onRestoreConfirmed(ids)
                },
            )

            else -> Unit
        }
    }
}

@Composable
internal fun AppControlListScreen(
    stateSource: StateFlow<AppControlListViewModel.State> =
        MutableStateFlow(AppControlListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    sizeSortCaveatVisible: Boolean = false,
    onNavigateUp: () -> Unit = {},
    onTapRow: (InstallId) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onSortModeChanged: (SortSettings.Mode) -> Unit = {},
    onSortDirectionToggle: () -> Unit = {},
    onTagToggle: (FilterSettings.Tag) -> Unit = {},
    onTagsReset: () -> Unit = {},
    onSizeSortCaveatAck: () -> Unit = {},
    onExcludeSelected: (Set<InstallId>) -> Unit = {},
    onToggleSelected: (Set<InstallId>) -> Unit = {},
    onUninstallSelected: (Set<InstallId>) -> Unit = {},
    onForceStopSelected: (Set<InstallId>) -> Unit = {},
    onArchiveSelected: (Set<InstallId>) -> Unit = {},
    onRestoreSelected: (Set<InstallId>) -> Unit = {},
    onExportSelected: (Set<InstallId>) -> Unit = {},
    onShareSelected: (Set<InstallId>) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state.rows

    var selection by remember { mutableStateOf<Set<InstallId>>(emptySet()) }
    val rowIds = rows?.map { it.installId }?.toSet() ?: emptySet()
    LaunchedEffect(rowIds) {
        selection = selection intersect rowIds
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    var overflowOpen by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var activeSheet by rememberSaveable { mutableStateOf<Sheet?>(null) }

    BackHandler(enabled = searchActive && selection.isEmpty() && activeSheet == null) {
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
        } else if (searchImeWasShown && activeSheet == null && selection.isEmpty()) {
            onSearchQueryChanged("")
            searchActive = false
        }
    }

    // Auto-open the search field if a query is restored (e.g. after process death) so the user
    // can see what's filtering the list.
    LaunchedEffect(state.options.searchQuery) {
        if (state.options.searchQuery.isNotEmpty() && !searchActive) searchActive = true
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val openSheet: (Sheet) -> Unit = { sheet ->
        keyboardController?.hide()
        activeSheet = sheet
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state = topBarState)
    val density = LocalDensity.current
    val filterRowHeight = 56.dp
    val filterRowHeightPx = with(density) { filterRowHeight.roundToPx() }
    LaunchedEffect(filterRowHeightPx) {
        topBarState.heightOffsetLimit = -filterRowHeightPx.toFloat()
    }
    // Reset the row offset so it's visible again when leaving selection mode.
    LaunchedEffect(selection.isEmpty()) {
        if (selection.isNotEmpty()) topBarState.heightOffset = 0f
    }

    val sortNonDefault = state.options.listSort != SortSettings()

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
                Surface(color = TopAppBarDefaults.topAppBarColors().containerColor) {
                    Column {
                        TopAppBar(
                            title = {
                                if (searchActive) {
                                    ToolbarSearchField(
                                        initial = state.options.searchQuery,
                                        onQueryChange = onSearchQueryChanged,
                                        onClose = {
                                            onSearchQueryChanged("")
                                            searchActive = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Column {
                                        Text(stringResource(CommonR.string.appcontrol_tool_name))
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
                        val visibleHeight = with(density) {
                            (filterRowHeightPx.toFloat() + topBarState.heightOffset).coerceAtLeast(0f).toDp()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(visibleHeight)
                                .clipToBounds(),
                        ) {
                            AppControlFilterRow(
                                modifier = Modifier.offset { IntOffset(0, topBarState.heightOffset.toInt()) },
                                activeTags = state.options.listFilter.tags,
                                sortNonDefault = sortNonDefault,
                                allowFilterActive = state.allowFilterActive,
                                onTagRemove = onTagToggle,
                                onAddTags = { openSheet(Sheet.Tags) },
                                onSort = { openSheet(Sheet.Sort) },
                            )
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(
                                imageVector = Icons.TwoTone.Close,
                                contentDescription = stringResource(CommonR.string.general_close_action),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val ids = selection
                            onUninstallSelected(ids)
                        }) {
                            Icon(
                                Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_selected_action),
                            )
                        }
                        IconButton(onClick = {
                            val ids = selection
                            selection = emptySet()
                            onExcludeSelected(ids)
                        }) {
                            Icon(
                                SdmIcons.ShieldAdd,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                        if (selection.size < rowIds.size) {
                            IconButton(onClick = { selection = rowIds }) {
                                Icon(
                                    Icons.TwoTone.SelectAll,
                                    contentDescription = stringResource(CommonR.string.general_list_select_all_action),
                                )
                            }
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                imageVector = Icons.TwoTone.MoreVert,
                                contentDescription = stringResource(CommonR.string.general_options_label),
                            )
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            if (state.allowActionToggle) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_toggle_app_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.AcUnit, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        onToggleSelected(selection)
                                    },
                                )
                            }
                            if (state.allowActionForceStop) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_force_stop_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.PowerSettingsNew, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        onForceStopSelected(selection)
                                    },
                                )
                            }
                            if (state.allowActionArchive) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_archive_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.Archive, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        onArchiveSelected(selection)
                                    },
                                )
                            }
                            if (state.allowActionRestore) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_restore_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.Unarchive, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        onRestoreSelected(selection)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appcontrol_export_app_selection_action)) },
                                leadingIcon = { Icon(Icons.TwoTone.SaveAlt, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    onExportSelected(selection)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appcontrol_share_list_action)) },
                                leadingIcon = { Icon(Icons.TwoTone.Share, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    onShareSelected(selection)
                                },
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
                        val spanCount = remember(maxWidth) { context.getSpanCount(widthDp = 410) }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(rows, key = { it.installId.toString() }) { row ->
                                val isSelected = selection.contains(row.installId)
                                AppControlListRow(
                                    row = row,
                                    sortMode = state.options.listSort.mode,
                                    selected = isSelected,
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = if (isSelected) {
                                                selection - row.installId
                                            } else {
                                                selection + row.installId
                                            }
                                        } else {
                                            onTapRow(row.installId)
                                        }
                                    },
                                    onLongClick = {
                                        selection = selection + row.installId
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (activeSheet) {
        Sheet.Sort -> ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
        ) {
            AppControlSortSheetContent(
                sort = state.options.listSort,
                allowSortSize = state.allowSortSize,
                allowSortScreenTime = state.allowSortScreenTime,
                sizeSortCaveatVisible = sizeSortCaveatVisible,
                onSortModeChanged = onSortModeChanged,
                onSortDirectionToggle = onSortDirectionToggle,
                onSizeSortCaveatAck = onSizeSortCaveatAck,
            )
        }

        Sheet.Tags -> ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
        ) {
            AppControlTagsSheetContent(
                tags = state.options.listFilter.tags,
                allowFilterActive = state.allowFilterActive,
                onTagToggle = onTagToggle,
                onTagsReset = onTagsReset,
            )
        }

        null -> Unit
    }
}

private enum class Sheet { Sort, Tags }

@Composable
private fun ToolbarSearchField(
    initial: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(initial) {
        if (initial != query) query = initial
    }
    LaunchedEffect(query) {
        delay(300)
        if (query != initial) onQueryChange(query)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = modifier.focusRequester(focusRequester),
        placeholder = { Text(stringResource(CommonR.string.general_search_action)) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = {
                if (query.isEmpty()) {
                    onClose()
                } else {
                    query = ""
                    onQueryChange("")
                }
            }) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(CommonR.string.general_close_action),
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel)
                }
            }
        },
    )
}
