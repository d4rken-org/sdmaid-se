package eu.darken.sdmse.common.picker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.picker.items.PickerItemRow
import eu.darken.sdmse.common.picker.items.PickerSelectedRow
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.R as CommonR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PickerScreenHost(
    route: PickerRoute,
    vm: PickerViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route.request) { vm.setRequest(route.request) }

    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PickerViewModel.Event.ExitConfirmation -> showExitConfirm = true
            }
        }
    }

    BackHandler { vm.goBack() }

    PickerScreen(
        stateSource = vm.state,
        onNavigateUp = vm::goBack,
        onCancel = vm::cancel,
        onSave = vm::save,
        onHome = vm::home,
        onSelectAll = vm::selectAll,
        onRowClick = vm::onRowClick,
        onToggleSelect = vm::onToggleSelect,
        onRemoveSelected = vm::onRemoveSelected,
    )

    if (showExitConfirm) {
        SdmConfirmDialog(
            message = stringResource(UiR.string.picker_unsaved_confirmation_message),
            onDismissRequest = { showExitConfirm = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_discard_action),
                onClick = {
                    showExitConfirm = false
                    vm.cancel(confirmed = true)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { showExitConfirm = false },
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerScreen(
    stateSource: StateFlow<PickerViewModel.State> = MutableStateFlow(PickerViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onCancel: () -> Unit = {},
    onSave: () -> Unit = {},
    onHome: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onRowClick: (PickerViewModel.PickerRow) -> Unit = {},
    onToggleSelect: (PickerViewModel.PickerRow) -> Unit = {},
    onRemoveSelected: (PickerViewModel.SelectedRow) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    var overflowOpen by remember { mutableStateOf(false) }

    val navigatable = state.current != null

    val sheetScaffoldState = rememberBottomSheetScaffoldState()
    val hasSelection = state.selected.isNotEmpty()
    LaunchedEffect(hasSelection) {
        // Auto-expand the selected-paths sheet on the first selection; collapse to peek when empty
        // (legacy BottomSheetBehavior parity).
        if (hasSelection) sheetScaffoldState.bottomSheetState.expand()
        else sheetScaffoldState.bottomSheetState.partialExpand()
    }

    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetPeekHeight = 72.dp,
        sheetContent = {
            SelectedPathsPanel(
                selected = state.selected,
                onRemoveSelected = onRemoveSelected,
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(UiR.string.picker_select_paths_title))
                        state.current?.lookup?.path?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    // Inside a folder the arrow steps up one level (goBack); at the area root it
                    // shows Close and cancels the picker — matching the legacy nav-icon behavior.
                    SdmTooltipIconButton(
                        icon = if (navigatable) Icons.AutoMirrored.TwoTone.ArrowBack else Icons.TwoTone.Close,
                        label = if (navigatable) {
                            stringResource(CommonR.string.general_navigate_up_action)
                        } else {
                            stringResource(CommonR.string.general_close_action)
                        },
                        onClick = if (navigatable) onNavigateUp else onCancel,
                    )
                },
                actions = {
                    if (state.progress == null) {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Save,
                            label = stringResource(CommonR.string.general_save_action),
                            onClick = onSave,
                        )
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.MoreVert,
                            label = stringResource(CommonR.string.general_options_label),
                            onClick = { overflowOpen = true },
                        )
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            if (navigatable) {
                                // Only offer "jump to data-area root" when we're actually inside a
                                // folder — at the root it would be a no-op.
                                DropdownMenuItem(
                                    text = { Text(stringResource(UiR.string.picker_home_action)) },
                                    onClick = { overflowOpen = false; onHome() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(CommonR.string.general_list_select_all_action)) },
                                onClick = { overflowOpen = false; onSelectAll() },
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (state.progress != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Adaptive grid: one column on phones (identical to the legacy list, dividers kept),
                // multiple columns on wide/tablet layouts (legacy GridLayoutManager getSpanCount parity).
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 360.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { row ->
                        Column {
                            PickerItemRow(
                                row = row,
                                onClick = { onRowClick(row) },
                                onToggleSelect = { onToggleSelect(row) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPathsPanel(
    modifier: Modifier = Modifier,
    selected: List<PickerViewModel.SelectedRow>,
    onRemoveSelected: (PickerViewModel.SelectedRow) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = stringResource(UiR.string.picker_selected_paths_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = pluralStringResource(
                    UiR.plurals.picker_selected_paths_subtitle,
                    selected.size,
                    selected.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (selected.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    items(selected, key = { it.id }) { row ->
                        PickerSelectedRow(
                            row = row,
                            onRemove = { onRemoveSelected(row) },
                        )
                    }
                }
            }
        }
    }
}
