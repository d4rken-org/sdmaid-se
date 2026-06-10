package eu.darken.sdmse.appcontrol.ui.list

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.appcontrol.ui.list.items.AppControlListRow
import eu.darken.sdmse.appcontrol.ui.list.tour.AppControlListTour
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.FastScrollSection
import eu.darken.sdmse.common.compose.SdmFastScroller
import eu.darken.sdmse.common.compose.SdmFastScrollerDefaultMinItems
import eu.darken.sdmse.common.compose.SdmFastScrollerLaneWidth
import eu.darken.sdmse.common.compose.SdmModalBottomSheet
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.layout.SdmSearchBar
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.snackbar.ToolListEventHandler
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppControlListScreenHost(
    vm: AppControlListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Re-check permission-backed setup state (usage access, storage) — it may have changed
        // in system settings or the setup screen while this screen wasn't in front.
        vm.onScreenResume()
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingExportIds by remember { mutableStateOf<Set<InstallId>>(emptySet()) }
    var pendingConfirm by remember { mutableStateOf<AppControlListViewModel.Event?>(null) }
    var sizeSortCaveatVisible by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val ids = pendingExportIds
        pendingExportIds = emptySet()
        if (ids.isNotEmpty()) vm.onExportPathPicked(ids, result.data?.data)
    }

    ToolListEventHandler(
        events = vm.events,
        snackbarHostState = snackbarHostState,
        onShowExclusions = vm::onShowExclusionsList,
        taskResultDuration = SnackbarDuration.Short,
    ) { event ->
        when (event) {
            is AppControlListViewModel.Event.ConfirmToggle,
            is AppControlListViewModel.Event.ConfirmUninstall,
            is AppControlListViewModel.Event.ConfirmForceStop,
            is AppControlListViewModel.Event.ConfirmArchive,
            is AppControlListViewModel.Event.ConfirmRestore -> pendingConfirm = event

            is AppControlListViewModel.Event.ExportSelectPath -> {
                pendingExportIds = event.ids
                runCatching { exportLauncher.launch(event.intent) }
                    .onFailure { pendingExportIds = emptySet() }
            }

            is AppControlListViewModel.Event.ShareList -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, event.text)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, null)) }
            }

            AppControlListViewModel.Event.ShowSizeSortCaveat -> sizeSortCaveatVisible = true

            else -> Unit
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
        onToggleFastScroller = vm::onToggleFastScroller,
        onRefresh = { vm.onRefresh(refreshPkgCache = true) },
    )

    pendingConfirm?.let { ev ->
        val cancelAction = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            onClick = { pendingConfirm = null },
        )
        when (ev) {
            is AppControlListViewModel.Event.ConfirmToggle -> SdmConfirmDialog(
                title = stringResource(R.string.appcontrol_toggle_confirmation_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_toggle_confirmation_message_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(CommonR.string.general_continue),
                    onClick = {
                        val ids = ev.ids
                        pendingConfirm = null
                        vm.onToggleConfirmed(ids)
                    },
                ),
                negative = cancelAction,
            )

            is AppControlListViewModel.Event.ConfirmUninstall -> SdmConfirmDialog(
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
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(
                        if (ev.ids.size > 1) CommonR.string.general_delete_selected_action
                        else CommonR.string.general_delete_action,
                    ),
                    onClick = {
                        val ids = ev.ids
                        pendingConfirm = null
                        vm.onUninstallConfirmed(ids)
                    },
                ),
                negative = cancelAction,
            )

            is AppControlListViewModel.Event.ConfirmForceStop -> SdmConfirmDialog(
                title = stringResource(R.string.appcontrol_force_stop_confirm_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_force_stop_confirmation_message_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(R.string.appcontrol_force_stop_action),
                    onClick = {
                        val ids = ev.ids
                        pendingConfirm = null
                        vm.onForceStopConfirmed(ids)
                    },
                ),
                negative = cancelAction,
            )

            is AppControlListViewModel.Event.ConfirmArchive -> SdmConfirmDialog(
                title = stringResource(R.string.appcontrol_archive_confirmation_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_archive_confirmation_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(R.string.appcontrol_archive_action),
                    onClick = {
                        val ids = ev.ids
                        pendingConfirm = null
                        vm.onArchiveConfirmed(ids)
                    },
                ),
                negative = cancelAction,
            )

            is AppControlListViewModel.Event.ConfirmRestore -> SdmConfirmDialog(
                title = stringResource(R.string.appcontrol_restore_confirmation_title),
                message = pluralStringResource(
                    R.plurals.appcontrol_restore_confirmation_x,
                    ev.ids.size,
                    ev.ids.size,
                ),
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(R.string.appcontrol_restore_action),
                    onClick = {
                        val ids = ev.ids
                        pendingConfirm = null
                        vm.onRestoreConfirmed(ids)
                    },
                ),
                negative = cancelAction,
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
    onToggleFastScroller: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val rows = state.rows

    var selection by remember { mutableStateOf<Set<InstallId>>(emptySet()) }
    val rowIds = rows?.map { it.installId }?.toSet() ?: emptySet()
    LaunchedEffect(rowIds) {
        selection = selection intersect rowIds
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    var selectionOverflowOpen by remember { mutableStateOf(false) }
    var normalOverflowOpen by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var activeSheet by rememberSaveable { mutableStateOf<Sheet?>(null) }

    val gridState = rememberLazyGridState()

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

    // While a task is executing (scan or action), the list content is covered by the progress
    // overlay — filter/sort/search/refresh would act on data that's about to be replaced, so
    // those controls are hidden until the task finishes.
    val isBusy = state.progress != null
    // Reset the offset while hidden so the filter row reappears fully expanded.
    LaunchedEffect(isBusy) {
        if (isBusy) topBarState.heightOffset = 0f
    }

    val sortNonDefault = state.options.listSort != SortSettings()

    val subtitle = rows?.let { list ->
        if (state.progress == null) {
            pluralStringResource(CommonR.plurals.result_x_items, list.size, list.size)
        } else {
            null
        }
    }

    val tourController = LocalGuidedTourController.current
    val tourSession by tourController.session.collectAsStateWithLifecycle()
    val tourActive = tourSession != null
    val tourDef = remember { AppControlListTour.definition() }
    // Track whether we already tried to start the tour in this composition: rows can oscillate
    // (load → refresh → reload) and we don't want to retrigger a tour the user already dismissed.
    var tourStartAttempted by remember { mutableStateOf(false) }
    // Pin the first-row target ID at the moment the tour starts so the bubble doesn't jump if
    // rows reorder mid-tour (e.g., a background install changes ordering).
    var tourFirstRowId by remember { mutableStateOf<InstallId?>(null) }
    val rowsReady = !rows.isNullOrEmpty()
    LaunchedEffect(rowsReady) {
        if (!rowsReady || tourStartAttempted) return@LaunchedEffect
        tourStartAttempted = true
        if (!tourController.shouldStart(tourDef)) return@LaunchedEffect
        tourFirstRowId = rows?.firstOrNull()?.installId
        tourController.start(tourDef)
    }
    // Force the collapsing chip toolbar back into view at tour start so the filter/sort
    // targets are guaranteed visible. clickProtection blocks input from reaching the list
    // during the tour, so a one-time write is enough.
    LaunchedEffect(tourActive) {
        if (tourActive) topBarState.heightOffset = 0f
    }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                Surface(color = TopAppBarDefaults.topAppBarColors().containerColor) {
                    Column {
                        TopAppBar(
                            title = {
                                if (searchActive) {
                                    SdmSearchBar(
                                        query = state.options.searchQuery,
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
                                SdmTooltipIconButton(
                                    icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                                    label = stringResource(CommonR.string.general_navigate_up_action),
                                    onClick = onNavigateUp,
                                )
                            },
                            actions = {
                                if (!searchActive && !isBusy) {
                                    SdmTooltipIconButton(
                                        icon = Icons.TwoTone.Search,
                                        label = stringResource(CommonR.string.general_search_action),
                                        onClick = { searchActive = true },
                                        modifier = Modifier.guidedTourTarget(AppControlListTour.SEARCH_TARGET),
                                    )
                                    SdmTooltipIconButton(
                                        icon = Icons.TwoTone.MoreVert,
                                        label = stringResource(CommonR.string.general_options_label),
                                        onClick = { normalOverflowOpen = true },
                                    )
                                    DropdownMenu(
                                        expanded = normalOverflowOpen,
                                        onDismissRequest = { normalOverflowOpen = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(CommonR.string.general_refresh_action)) },
                                            leadingIcon = { Icon(Icons.TwoTone.Refresh, contentDescription = null) },
                                            onClick = {
                                                normalOverflowOpen = false
                                                onRefresh()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.appcontrol_list_fastscroller_action)) },
                                            leadingIcon = {
                                                if (state.fastScrollerEnabled) {
                                                    Icon(Icons.TwoTone.Check, contentDescription = null)
                                                } else {
                                                    Spacer(Modifier.size(24.dp))
                                                }
                                            },
                                            onClick = {
                                                normalOverflowOpen = false
                                                onToggleFastScroller()
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        val visibleHeight = with(density) {
                            (filterRowHeightPx.toFloat() + topBarState.heightOffset).coerceAtLeast(0f).toDp()
                        }
                        AnimatedVisibility(visible = !isBusy) {
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
                                    addTagsModifier = Modifier.guidedTourTarget(AppControlListTour.FILTER_TARGET),
                                    sortModifier = Modifier.guidedTourTarget(AppControlListTour.SORT_TARGET),
                                )
                            }
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Close,
                            label = stringResource(CommonR.string.general_close_action),
                            onClick = { selection = emptySet() },
                        )
                    },
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Delete,
                            label = stringResource(CommonR.string.general_delete_selected_action),
                            onClick = {
                                val ids = selection
                                selection = emptySet()
                                onUninstallSelected(ids)
                            },
                        )
                        SdmTooltipIconButton(
                            icon = SdmIcons.ShieldAdd,
                            label = stringResource(CommonR.string.general_exclude_selected_action),
                            onClick = {
                                val ids = selection
                                selection = emptySet()
                                onExcludeSelected(ids)
                            },
                        )
                        if (selection.size < rowIds.size) {
                            SdmTooltipIconButton(
                                icon = Icons.TwoTone.SelectAll,
                                label = stringResource(CommonR.string.general_list_select_all_action),
                                onClick = { selection = rowIds },
                            )
                        }
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.MoreVert,
                            label = stringResource(CommonR.string.general_options_label),
                            onClick = { selectionOverflowOpen = true },
                        )
                        DropdownMenu(expanded = selectionOverflowOpen, onDismissRequest = { selectionOverflowOpen = false }) {
                            if (state.allowActionToggle) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_toggle_app_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.AcUnit, contentDescription = null) },
                                    onClick = {
                                        val ids = selection
                                        selectionOverflowOpen = false
                                        selection = emptySet()
                                        onToggleSelected(ids)
                                    },
                                )
                            }
                            if (state.allowActionForceStop) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_force_stop_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.PowerSettingsNew, contentDescription = null) },
                                    onClick = {
                                        val ids = selection
                                        selectionOverflowOpen = false
                                        selection = emptySet()
                                        onForceStopSelected(ids)
                                    },
                                )
                            }
                            if (state.allowActionArchive) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_archive_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.Archive, contentDescription = null) },
                                    onClick = {
                                        val ids = selection
                                        selectionOverflowOpen = false
                                        selection = emptySet()
                                        onArchiveSelected(ids)
                                    },
                                )
                            }
                            if (state.allowActionRestore) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.appcontrol_restore_selection_action)) },
                                    leadingIcon = { Icon(Icons.TwoTone.Unarchive, contentDescription = null) },
                                    onClick = {
                                        val ids = selection
                                        selectionOverflowOpen = false
                                        selection = emptySet()
                                        onRestoreSelected(ids)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appcontrol_export_app_selection_action)) },
                                leadingIcon = { Icon(Icons.TwoTone.SaveAlt, contentDescription = null) },
                                onClick = {
                                    val ids = selection
                                    selectionOverflowOpen = false
                                    selection = emptySet()
                                    onExportSelected(ids)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appcontrol_share_list_action)) },
                                leadingIcon = { Icon(Icons.TwoTone.Share, contentDescription = null) },
                                onClick = {
                                    val ids = selection
                                    selectionOverflowOpen = false
                                    selection = emptySet()
                                    onShareSelected(ids)
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
                        val sections = remember(rows, state.options.listSort.mode) {
                            buildFastScrollerSections(rows, state.options.listSort.mode)
                        }
                        // Only carve out gutter space when the scroller will actually render —
                        // SdmFastScroller hides itself below SdmFastScrollerDefaultMinItems,
                        // so matching the predicate here avoids dead end-padding on short lists.
                        val fastScrollerVisible = state.fastScrollerEnabled &&
                            rows.size >= SdmFastScrollerDefaultMinItems
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = 8.dp,
                                end = if (fastScrollerVisible) SdmFastScrollerLaneWidth else 0.dp,
                            ),
                        ) {
                            items(rows, key = { it.installId.toString() }) { row ->
                                val isSelected = selection.contains(row.installId)
                                val rowModifier = if (row.installId == tourFirstRowId) {
                                    Modifier.guidedTourTarget(AppControlListTour.APP_ROW_TARGET)
                                } else {
                                    Modifier
                                }
                                AppControlListRow(
                                    modifier = rowModifier,
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
                        if (fastScrollerVisible) {
                            SdmFastScroller(
                                state = gridState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                sections = sections,
                            )
                        }
                    }
                }
            }
        }
    }

    when (activeSheet) {
        Sheet.Sort -> SdmModalBottomSheet(onDismiss = { activeSheet = null }) {
            AppControlSortSheetContent(
                sort = state.options.listSort,
                sizeSortModuleEnabled = state.sizeSortModuleEnabled,
                sizeSortCaveatVisible = sizeSortCaveatVisible,
                onSortModeChanged = onSortModeChanged,
                onSortDirectionToggle = onSortDirectionToggle,
                onSizeSortCaveatAck = onSizeSortCaveatAck,
            )
        }

        Sheet.Tags -> SdmModalBottomSheet(onDismiss = { activeSheet = null }) {
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

internal fun buildFastScrollerSections(
    rows: List<AppControlListViewModel.Row>,
    sortMode: SortSettings.Mode,
): List<FastScrollSection> {
    val keyOf: (AppControlListViewModel.Row) -> String? = when (sortMode) {
        SortSettings.Mode.NAME -> { row -> row.sectionKeyName }
        SortSettings.Mode.PACKAGENAME -> { row -> row.sectionKeyPkg }
        // SIZE / dates / SCREEN_TIME have null-prone source data (estimated sizes, epoch dates,
        // negative durations for missing screen time). Skip section labels for those — the thumb
        // alone is still useful.
        else -> { _ -> null }
    }
    val sections = mutableListOf<FastScrollSection>()
    var previous: String? = null
    rows.forEachIndexed { index, row ->
        val key = keyOf(row) ?: return@forEachIndexed
        if (key != previous) {
            sections += FastScrollSection(itemIndex = index, label = key)
            previous = key
        }
    }
    return sections
}

