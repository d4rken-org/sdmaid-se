package eu.darken.sdmse.swiper.ui.status

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.status.items.SwiperStatusRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun SwiperStatusScreenHost(
    route: eu.darken.sdmse.swiper.ui.SwiperStatusRoute,
    vm: SwiperStatusViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    androidx.compose.runtime.LaunchedEffect(route) { vm.bindRoute(route) }

    SwiperStatusScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onItemClick = vm::navigateToItem,
        onReset = vm::resetDecision,
        onQuickKeep = vm::markKeep,
        onQuickDelete = vm::markDelete,
        onFinalize = {
            vm.retryAllFailed()
            vm.finalize()
        },
        onDone = vm::done,
        onKeepSelected = { items -> vm.updateDecisions(items, SwipeDecision.KEEP) },
        onDeleteSelected = { items -> vm.updateDecisions(items, SwipeDecision.DELETE) },
        onResetSelected = { items -> vm.updateDecisions(items, SwipeDecision.UNDECIDED) },
        onExcludeSelected = vm::excludeAndRemove,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SwiperStatusScreen(
    stateSource: StateFlow<SwiperStatusViewModel.State> = MutableStateFlow(SwiperStatusViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onItemClick: (Long) -> Unit = {},
    onReset: (Long) -> Unit = {},
    onQuickKeep: (Long) -> Unit = {},
    onQuickDelete: (Long) -> Unit = {},
    onFinalize: () -> Unit = {},
    onDone: () -> Unit = {},
    onKeepSelected: (List<SwipeItem>) -> Unit = {},
    onDeleteSelected: (List<SwipeItem>) -> Unit = {},
    onResetSelected: (List<SwipeItem>) -> Unit = {},
    onExcludeSelected: (List<SwipeItem>) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val items = state.items
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val itemIds = items.map { it.id }.toSet()
    LaunchedEffect(itemIds) { selection = selection intersect itemIds }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val selectedItems = items.filter { selection.contains(it.id) }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.swiper_status_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (state.finalizeAction != SwiperStatusViewModel.FinalizeAction.HIDDEN) {
                            FinalizeIconButton(
                                state = state,
                                onClick = {
                                    dispatchFinalize(state, onFinalize, onDone, openConfirm = { confirmDelete = true })
                                },
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onKeepSelected(payload)
                        }) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = stringResource(R.string.swiper_keep_action),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onDeleteSelected(payload)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_action),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onResetSelected(payload)
                        }) {
                            Icon(
                                Icons.Filled.Restore,
                                contentDescription = stringResource(CommonR.string.general_reset_action),
                            )
                        }
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onExcludeSelected(payload)
                        }) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            StatsSection(
                state = state,
                onFinalizeClick = {
                    dispatchFinalize(state, onFinalize, onDone, openConfirm = { confirmDelete = true })
                },
                onUndecidedClick = {
                    val firstUndecided = items.indexOfFirst { it.decision == SwipeDecision.UNDECIDED }
                    if (firstUndecided >= 0) {
                        scope.launch { listState.animateScrollToItem(firstUndecided) }
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val isSelected = selection.contains(item.id)
                    SwiperStatusRow(
                        item = item,
                        selected = isSelected,
                        selectionActive = selection.isNotEmpty(),
                        onClick = {
                            if (selection.isNotEmpty()) {
                                selection = if (isSelected) selection - item.id else selection + item.id
                            } else {
                                onItemClick(item.id)
                            }
                        },
                        onLongClick = { selection = selection + item.id },
                        onReset = { onReset(item.id) },
                        onQuickKeep = { onQuickKeep(item.id) },
                        onQuickDelete = { onQuickDelete(item.id) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val (deleteSizeFormatted, _) = ByteFormatter.formatSize(context, state.deleteSize)
        val deleteMsg = pluralStringResource(
            R.plurals.swiper_delete_confirmation_message,
            state.deleteCount,
            state.deleteCount,
            deleteSizeFormatted,
        )
        val message = if (state.undecidedCount > 0) {
            val undecidedMsg = pluralStringResource(
                R.plurals.swiper_delete_confirmation_message_partial_undecided,
                state.undecidedCount,
                state.undecidedCount,
            )
            "$deleteMsg\n\n$undecidedMsg"
        } else {
            deleteMsg
        }

        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.swiper_delete_confirmation_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onFinalize()
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

private fun dispatchFinalize(
    state: SwiperStatusViewModel.State,
    onFinalize: () -> Unit,
    onDone: () -> Unit,
    openConfirm: () -> Unit,
) {
    when (state.finalizeAction) {
        SwiperStatusViewModel.FinalizeAction.DONE -> onDone()
        SwiperStatusViewModel.FinalizeAction.DELETE -> openConfirm()
        SwiperStatusViewModel.FinalizeAction.APPLY -> onFinalize()
        SwiperStatusViewModel.FinalizeAction.HIDDEN -> {}
    }
}

@Composable
private fun FinalizeIconButton(
    state: SwiperStatusViewModel.State,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when (state.finalizeAction) {
        SwiperStatusViewModel.FinalizeAction.DELETE -> Icons.Filled.DeleteForever
        SwiperStatusViewModel.FinalizeAction.APPLY -> Icons.Filled.Check
        SwiperStatusViewModel.FinalizeAction.DONE -> Icons.Filled.Favorite
        SwiperStatusViewModel.FinalizeAction.HIDDEN -> return
    }
    val enabled = when (state.finalizeAction) {
        SwiperStatusViewModel.FinalizeAction.DONE -> state.canDone
        else -> state.canFinalize
    }
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (state.finalizeAction == SwiperStatusViewModel.FinalizeAction.DELETE) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun StatsSection(
    state: SwiperStatusViewModel.State,
    onFinalizeClick: () -> Unit,
    onUndecidedClick: () -> Unit,
) {
    val context = LocalContext.current
    val (keepSize, _) = ByteFormatter.formatSize(context, state.keepSize)
    val (deleteSize, _) = ByteFormatter.formatSize(context, state.deleteSize)
    val (undecidedSize, _) = ByteFormatter.formatSize(context, state.undecidedSize)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatRow(
            icon = Icons.Filled.Favorite,
            tint = MaterialTheme.colorScheme.primary,
            primary = pluralStringResource(
                R.plurals.swiper_session_status_to_keep,
                state.keepCount,
                state.keepCount,
            ),
            sizeText = "($keepSize)",
            trailing = {
                if (state.alreadyKeptCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.swiper_session_status_kept,
                            state.alreadyKeptCount,
                            state.alreadyKeptCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
        StatRow(
            icon = Icons.Filled.Delete,
            tint = MaterialTheme.colorScheme.error,
            primary = pluralStringResource(
                R.plurals.swiper_session_status_to_delete,
                state.deleteCount,
                state.deleteCount,
            ),
            sizeText = "($deleteSize)",
            trailing = {
                if (state.alreadyDeletedCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.swiper_session_status_deleted,
                            state.alreadyDeletedCount,
                            state.alreadyDeletedCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
        if (state.undecidedCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUndecidedClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.swiper_session_status_undecided,
                        state.undecidedCount,
                        state.undecidedCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "($undecidedSize)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FinalizeButton(
            state = state,
            onClick = onFinalizeClick,
        )
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    primary: String,
    sizeText: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(primary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing()
    }
}

@Composable
private fun FinalizeButton(
    state: SwiperStatusViewModel.State,
    onClick: () -> Unit,
) {
    val action = state.finalizeAction
    if (action == SwiperStatusViewModel.FinalizeAction.HIDDEN) return

    val label = when (action) {
        SwiperStatusViewModel.FinalizeAction.DELETE -> {
            if (state.undecidedCount > 0) {
                stringResource(R.string.swiper_delete_x_action, state.deleteCount)
            } else {
                stringResource(CommonR.string.general_delete_action)
            }
        }

        SwiperStatusViewModel.FinalizeAction.APPLY -> stringResource(CommonR.string.general_apply_action)
        SwiperStatusViewModel.FinalizeAction.DONE -> stringResource(CommonR.string.general_done_action)
        SwiperStatusViewModel.FinalizeAction.HIDDEN -> return
    }

    val icon = when (action) {
        SwiperStatusViewModel.FinalizeAction.DELETE -> Icons.Filled.DeleteForever
        SwiperStatusViewModel.FinalizeAction.APPLY -> Icons.Filled.Check
        SwiperStatusViewModel.FinalizeAction.DONE -> Icons.Filled.Favorite
        SwiperStatusViewModel.FinalizeAction.HIDDEN -> return
    }

    val enabled = when (action) {
        SwiperStatusViewModel.FinalizeAction.DONE -> state.canDone
        else -> state.canFinalize
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (action == SwiperStatusViewModel.FinalizeAction.DELETE) {
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        } else {
            FilledTonalButton(onClick = onClick, enabled = enabled) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

@Preview2
@Composable
private fun SwiperStatusScreenEmptyPreview() {
    PreviewWrapper {
        SwiperStatusScreen(
            stateSource = MutableStateFlow(SwiperStatusViewModel.State()),
        )
    }
}
