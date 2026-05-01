package eu.darken.sdmse.main.ui.settings.cards

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.ui.settings.cards.items.DashboardCardConfigDragHandle
import eu.darken.sdmse.main.ui.settings.cards.items.DashboardCardConfigHeader
import eu.darken.sdmse.main.ui.settings.cards.items.DashboardCardConfigRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DashboardCardConfigScreenHost(
    vm: DashboardCardConfigViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    DashboardCardConfigScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onToggleVisibility = vm::toggleVisibility,
        onReorder = vm::applyReorder,
        onReset = vm::resetToDefaults,
    )
}

@Composable
internal fun DashboardCardConfigScreen(
    stateSource: StateFlow<DashboardCardConfigViewModel.State> =
        MutableStateFlow(DashboardCardConfigViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onToggleVisibility: (DashboardCardType) -> Unit = {},
    onReorder: (List<DashboardCardType>) -> Unit = {},
    onReset: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    // Local order mirror. Updated instantly during drag (so the LazyColumn reacts).
    // Between drag-end and the DataStore write landing, `pendingOrder` holds the just-committed
    // order so the resync block doesn't clobber `localOrder` with the stale `vmOrder`.
    var isDragging by remember { mutableStateOf(false) }
    var pendingOrder by remember { mutableStateOf<List<DashboardCardType>?>(null) }
    var localOrder by remember { mutableStateOf(state.entries.map { it.type }) }
    val vmOrder = state.entries.map { it.type }
    LaunchedEffect(vmOrder, isDragging) {
        if (pendingOrder != null && vmOrder == pendingOrder) pendingOrder = null
        if (!isDragging && pendingOrder == null && vmOrder != localOrder) localOrder = vmOrder
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        val fromKey = from.key as? DashboardCardType ?: return@rememberReorderableLazyListState
        val toKey = to.key as? DashboardCardType ?: return@rememberReorderableLazyListState
        val fromIndex = localOrder.indexOf(fromKey)
        val toIndex = localOrder.indexOf(toKey)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        localOrder = localOrder.toMutableList().also {
            val moved = it.removeAt(fromIndex)
            it.add(toIndex, moved)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_card_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.TwoTone.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(CommonR.string.general_reset_action)) },
                            onClick = {
                                showMenu = false
                                onReset()
                            },
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            item(key = "header") {
                DashboardCardConfigHeader()
            }
            items(localOrder, key = { it }) { type ->
                ReorderableItem(state = reorderState, key = type) { _ ->
                    val isVisible = state.entries.firstOrNull { it.type == type }?.isVisible ?: true
                    DashboardCardConfigRow(
                        type = type,
                        isVisible = isVisible,
                        onToggleVisibility = { onToggleVisibility(type) },
                        dragHandle = {
                            DashboardCardConfigDragHandle(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { isDragging = true },
                                    onDragStopped = {
                                        isDragging = false
                                        pendingOrder = localOrder
                                        onReorder(localOrder)
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun DashboardCardConfigScreenPreview() {
    PreviewWrapper {
        DashboardCardConfigScreen(
            stateSource = MutableStateFlow(
                DashboardCardConfigViewModel.State(
                    entries = DashboardCardType.entries.map { DashboardCardConfig.CardEntry(it) },
                ),
            ),
        )
    }
}
