package eu.darken.sdmse.swiper.ui.status

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
                            Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
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
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onKeepSelected(payload)
                        }) {
                            Icon(
                                Icons.TwoTone.Favorite,
                                contentDescription = stringResource(R.string.swiper_status_action_keep_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onDeleteSelected(payload)
                        }) {
                            Icon(
                                Icons.TwoTone.Delete,
                                contentDescription = stringResource(R.string.swiper_status_action_delete_selected),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onResetSelected(payload)
                        }) {
                            Icon(
                                Icons.TwoTone.Restore,
                                contentDescription = stringResource(R.string.swiper_status_action_reset_selected),
                            )
                        }
                        IconButton(onClick = {
                            val payload = selectedItems
                            selection = emptySet()
                            onExcludeSelected(payload)
                        }) {
                            Icon(
                                Icons.TwoTone.Shield,
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
        val undecidedNotice = if (state.undecidedCount > 0) {
            pluralStringResource(
                R.plurals.swiper_delete_confirmation_message_partial_undecided,
                state.undecidedCount,
                state.undecidedCount,
            )
        } else {
            null
        }

        // Dialog-local state — recreated when the dialog re-opens (key on confirmDelete via the
        // surrounding `if`). Without this scoping, a previously-checked box would carry across
        // a later finalize attempt, defeating the gate.
        var understandChecked by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.swiper_delete_confirmation_title)) },
            text = {
                DeleteConfirmationBody(
                    deleteMsg = deleteMsg,
                    undecidedNotice = undecidedNotice,
                    hasSensitiveRoot = state.hasSensitiveRoot,
                    deletionPreview = state.deletionPreview,
                    understandChecked = understandChecked,
                    onUnderstandToggle = { understandChecked = it },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.hasSensitiveRoot || understandChecked,
                    onClick = {
                        confirmDelete = false
                        onFinalize()
                    },
                ) {
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

@Composable
private fun DeleteConfirmationBody(
    deleteMsg: String,
    undecidedNotice: String?,
    hasSensitiveRoot: Boolean,
    deletionPreview: DeletionPreview,
    understandChecked: Boolean,
    onUnderstandToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Column {
        Text(deleteMsg)
        if (undecidedNotice != null) {
            Spacer(Modifier.height(12.dp))
            Text(undecidedNotice)
        }
        if (hasSensitiveRoot) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.swiper_delete_confirmation_sensitive_root_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (deletionPreview.buckets.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            deletionPreview.buckets.forEach { bucket ->
                val (sizeFormatted, _) = ByteFormatter.formatSize(context, bucket.size)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${bucket.count} • $sizeFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (deletionPreview.moreFolders > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.swiper_delete_confirmation_more_folders,
                        deletionPreview.moreFolders,
                        deletionPreview.moreFolders,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasSensitiveRoot) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUnderstandToggle(!understandChecked) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = understandChecked,
                    onCheckedChange = onUnderstandToggle,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.swiper_delete_confirmation_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
        SwiperStatusViewModel.FinalizeAction.DELETE -> Icons.TwoTone.DeleteForever
        SwiperStatusViewModel.FinalizeAction.APPLY -> Icons.TwoTone.Check
        SwiperStatusViewModel.FinalizeAction.DONE -> Icons.TwoTone.Favorite
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
            icon = Icons.TwoTone.Favorite,
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
                        imageVector = Icons.TwoTone.CheckCircle,
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
            icon = Icons.TwoTone.Delete,
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
                        imageVector = Icons.TwoTone.CheckCircle,
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
                    imageVector = Icons.AutoMirrored.TwoTone.HelpOutline,
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
        SwiperStatusViewModel.FinalizeAction.DELETE -> Icons.TwoTone.DeleteForever
        SwiperStatusViewModel.FinalizeAction.APPLY -> Icons.TwoTone.Check
        SwiperStatusViewModel.FinalizeAction.DONE -> Icons.TwoTone.Favorite
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
