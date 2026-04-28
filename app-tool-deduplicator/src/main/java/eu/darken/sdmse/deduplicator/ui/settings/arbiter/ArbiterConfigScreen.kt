package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.ui.settings.arbiter.items.ArbiterConfigDragHandle
import eu.darken.sdmse.deduplicator.ui.settings.arbiter.items.ArbiterConfigExplanationCard
import eu.darken.sdmse.deduplicator.ui.settings.arbiter.items.ArbiterCriteriumRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ArbiterConfigScreenHost(
    vm: ArbiterConfigViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    var modeSelection by remember { mutableStateOf<ArbiterConfigViewModel.Event.ShowModeSelection?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ArbiterConfigViewModel.Event.ShowModeSelection -> modeSelection = event
            }
        }
    }

    ArbiterConfigScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onCriteriumClick = vm::onCriteriumClick,
        onReorder = vm::onItemsReordered,
        onReset = vm::resetToDefaults,
    )

    modeSelection?.let { event ->
        ModeSelectionDialog(
            criterium = event.criterium,
            modes = event.modes,
            onDismiss = { modeSelection = null },
            onSelected = { mode ->
                vm.onModeSelected(event.criterium, mode)
                modeSelection = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArbiterConfigScreen(
    stateSource: StateFlow<ArbiterConfigViewModel.State> =
        MutableStateFlow(ArbiterConfigViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onCriteriumClick: (ArbiterCriterium) -> Unit = {},
    onReorder: (List<ArbiterCriterium>) -> Unit = {},
    onReset: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    var showOverflow by remember { mutableStateOf(false) }

    // Local mirror for responsive drag-reorder. Reconcile with VM state when not dragging.
    var isDragging by remember { mutableStateOf(false) }
    var pendingOrder by remember { mutableStateOf<List<ArbiterCriterium>?>(null) }
    var localOrder by remember { mutableStateOf(state.criteria) }
    LaunchedEffect(state.criteria, isDragging) {
        if (pendingOrder != null && state.criteria == pendingOrder) pendingOrder = null
        if (!isDragging && pendingOrder == null && state.criteria != localOrder) {
            localOrder = state.criteria
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        val fromIndex = localOrder.indexOfFirst { it::class.simpleName == fromKey }
        val toIndex = localOrder.indexOfFirst { it::class.simpleName == toKey }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        localOrder = localOrder.toMutableList().also {
            val moved = it.removeAt(fromIndex)
            it.add(toIndex, moved)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deduplicator_arbiter_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.TwoTone.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(CommonR.string.general_reset_action)) },
                            onClick = {
                                showOverflow = false
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
            item(key = "header") { ArbiterConfigExplanationCard() }
            items(localOrder, key = { it::class.simpleName ?: it.toString() }) { criterium ->
                ReorderableItem(
                    state = reorderState,
                    key = criterium::class.simpleName ?: criterium.toString(),
                ) { _ ->
                    ArbiterCriteriumRow(
                        criterium = criterium,
                        onClick = { onCriteriumClick(criterium) },
                        dragHandle = {
                            ArbiterConfigDragHandle(
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

@Composable
private fun ModeSelectionDialog(
    criterium: ArbiterCriterium,
    modes: List<ArbiterCriterium.Mode>,
    onDismiss: () -> Unit,
    onSelected: (ArbiterCriterium.Mode) -> Unit,
) {
    val currentMode = criterium.criteriumMode()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deduplicator_arbiter_select_mode_title)) },
        text = {
            LazyColumn {
                items(modes) { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onSelected(mode) },
                        )
                        Text(text = stringResource(mode.labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Preview2
@Composable
private fun ArbiterConfigScreenPreview() {
    PreviewWrapper {
        ArbiterConfigScreen(
            stateSource = MutableStateFlow(
                ArbiterConfigViewModel.State(
                    criteria = listOf(
                        ArbiterCriterium.DuplicateType(),
                        ArbiterCriterium.PreferredPath(),
                        ArbiterCriterium.MediaProvider(),
                        ArbiterCriterium.Location(),
                        ArbiterCriterium.Nesting(),
                        ArbiterCriterium.Modified(),
                        ArbiterCriterium.Size(),
                    ),
                ),
            ),
        )
    }
}
