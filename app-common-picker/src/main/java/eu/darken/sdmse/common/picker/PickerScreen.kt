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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        onCancel = vm::cancel,
        onSave = vm::save,
        onHome = vm::home,
        onSelectAll = vm::selectAll,
        onRowClick = vm::onRowClick,
        onToggleSelect = vm::onToggleSelect,
        onRemoveSelected = vm::onRemoveSelected,
    )

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            text = { Text(stringResource(UiR.string.picker_unsaved_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    vm.cancel(confirmed = true)
                }) {
                    Text(stringResource(CommonR.string.general_discard_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@Composable
internal fun PickerScreen(
    stateSource: StateFlow<PickerViewModel.State> = MutableStateFlow(PickerViewModel.State()),
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

    Scaffold(
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
                    IconButton(onClick = onCancel) {
                        if (navigatable) {
                            Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                        } else {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (state.progress == null) {
                        IconButton(onClick = onSave) {
                            Icon(Icons.TwoTone.Save, contentDescription = stringResource(CommonR.string.general_save_action))
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.TwoTone.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(UiR.string.picker_home_action)) },
                                onClick = { overflowOpen = false; onHome() },
                            )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (state.progress != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { row ->
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

            SelectedPathsPanel(
                selected = state.selected,
                onRemoveSelected = onRemoveSelected,
            )
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
