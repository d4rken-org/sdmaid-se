package eu.darken.sdmse.squeezer.ui.list

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ListAlt
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.darken.sdmse.common.compose.layout.SdmEmptyState
import eu.darken.sdmse.common.compose.layout.SdmExcludeAction
import eu.darken.sdmse.common.compose.layout.SdmListDefaults
import eu.darken.sdmse.common.compose.layout.SdmSelectAllAction
import eu.darken.sdmse.common.compose.layout.SdmSelectionTopAppBar
import eu.darken.sdmse.common.compose.layout.SdmTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.ui.comparison.SqueezerComparisonDialog
import eu.darken.sdmse.squeezer.ui.list.items.SqueezerListGridCard
import eu.darken.sdmse.squeezer.ui.list.items.SqueezerListLinearRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private data class PendingConfirmation(
    val items: List<CompressibleMedia>,
    val quality: Int,
)

private data class ComparisonRequest(
    val media: CompressibleMedia,
    val quality: Int,
)

@Composable
fun SqueezerListScreenHost(
    vm: SqueezerListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var pendingConfirmation by remember { mutableStateOf<PendingConfirmation?>(null) }
    var comparisonRequest by remember { mutableStateOf<ComparisonRequest?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SqueezerListViewModel.Event.ConfirmCompression -> {
                    pendingConfirmation = PendingConfirmation(
                        items = event.items,
                        quality = event.quality,
                    )
                }

                is SqueezerListViewModel.Event.ExclusionsCreated -> snackScope.launch {
                    val message = context.resources.getQuantityString(
                        CommonR.plurals.exclusion_x_new_exclusions,
                        event.count,
                        event.count,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(CommonR.string.general_view_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.openExclusionsList()
                    }
                }

                is SqueezerListViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    SqueezerListScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onCompressAll = vm::compressAll,
        onCompressIds = { ids -> vm.compress(ids) },
        onExcludeIds = { ids -> vm.exclude(ids) },
        onToggleLayoutMode = vm::toggleLayoutMode,
        onPreviewMedia = vm::openPreview,
    )

    val pending = pendingConfirmation
    if (pending != null && comparisonRequest == null) {
        SqueezerPreviewCompressionDialog(
            items = pending.items,
            quality = pending.quality,
            onCompress = { quality ->
                val ids = pending.items.map { it.identifier }.toSet()
                pendingConfirmation = null
                vm.compress(ids, confirmed = true, qualityOverride = quality)
            },
            onDismiss = { pendingConfirmation = null },
            onViewComparison = {
                val sample = pending.items.firstOrNull() ?: return@SqueezerPreviewCompressionDialog
                comparisonRequest = ComparisonRequest(media = sample, quality = pending.quality)
            },
        )
    }

    comparisonRequest?.let { request ->
        SqueezerComparisonDialog(
            media = request.media,
            quality = request.quality,
            onClose = { comparisonRequest = null },
        )
    }
}

@Composable
internal fun SqueezerListScreen(
    stateSource: StateFlow<SqueezerListViewModel.State> = MutableStateFlow(SqueezerListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onCompressAll: () -> Unit = {},
    onCompressIds: (Set<CompressibleMedia.Id>) -> Unit = {},
    onExcludeIds: (Set<CompressibleMedia.Id>) -> Unit = {},
    onToggleLayoutMode: () -> Unit = {},
    onPreviewMedia: (CompressibleMedia) -> Unit = {},
) {
    val context = LocalContext.current
    val state by stateSource.collectAsStateWithLifecycle()
    val media = state.media ?: emptyList()
    val itemIds = remember(media) { media.map { it.identifier }.toSet() }

    var selection by remember { mutableStateOf<Set<CompressibleMedia.Id>>(emptySet()) }

    LaunchedEffect(itemIds) {
        selection = selection intersect itemIds
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val totalSavings = media.sumOf { it.estimatedSavings ?: 0L }
    val selectedSavings = media.filter { it.identifier in selection }.sumOf { it.estimatedSavings ?: 0L }
    val subtitle = if (state.progress == null && media.isNotEmpty()) {
        val countText = pluralStringResource(
            CommonR.plurals.result_x_items,
            media.size,
            media.size,
        )
        if (totalSavings > 0) "$countText • ~" + Formatter.formatShortFileSize(context, totalSavings) else countText
    } else {
        null
    }
    val selectionSubtitle = selectedSavings
        .takeIf { it > 0 }
        ?.let { "~" + Formatter.formatShortFileSize(context, it) }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                SdmTopAppBar(
                    title = stringResource(CommonR.string.squeezer_tool_name),
                    subtitle = subtitle,
                    onNavigateUp = onNavigateUp,
                    actions = {
                        if (state.progress == null && media.isNotEmpty()) {
                            IconButton(onClick = onToggleLayoutMode) {
                                Icon(
                                    imageVector = when (state.layoutMode) {
                                        LayoutMode.LINEAR -> Icons.TwoTone.GridView
                                        LayoutMode.GRID -> Icons.AutoMirrored.TwoTone.ListAlt
                                    },
                                    contentDescription = stringResource(CommonR.string.general_toggle_layout_mode),
                                )
                            }
                        }
                    },
                )
            } else {
                SdmSelectionTopAppBar(
                    selectedCount = selection.size,
                    subtitle = selectionSubtitle,
                    onClearSelection = { selection = emptySet() },
                    actions = {
                        IconButton(onClick = { onCompressIds(selection) }) {
                            Icon(
                                imageVector = Icons.TwoTone.Compress,
                                contentDescription = stringResource(R.string.squeezer_compress_action),
                            )
                        }
                        SdmExcludeAction(onClick = {
                            onExcludeIds(selection)
                            selection = emptySet()
                        })
                        SdmSelectAllAction(
                            visible = selection.size < itemIds.size,
                            onClick = { selection = itemIds },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (state.progress == null && media.isNotEmpty() && selection.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCompressAll,
                    icon = {
                        Icon(
                            imageVector = Icons.TwoTone.Compress,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.squeezer_compress_all_action)) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    elevation = FloatingActionButtonDefaults.elevation(),
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
                    state.media == null -> Box(modifier = Modifier.fillMaxSize())

                    media.isEmpty() -> SdmEmptyState()

                    state.layoutMode == LayoutMode.LINEAR -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(media, key = { it.identifier.value }) { item ->
                            SqueezerListLinearRow(
                                media = item,
                                isSelected = item.identifier in selection,
                                onTap = {
                                    if (selection.isEmpty()) {
                                        onCompressIds(setOf(item.identifier))
                                    } else {
                                        selection = if (item.identifier in selection) {
                                            selection - item.identifier
                                        } else {
                                            selection + item.identifier
                                        }
                                    }
                                },
                                onLongPress = {
                                    selection = selection + item.identifier
                                },
                                onPreviewTap = {
                                    if (selection.isEmpty()) onPreviewMedia(item)
                                },
                            )
                        }
                    }

                    state.layoutMode == LayoutMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(144.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = SdmListDefaults.GridTileContentPadding,
                    ) {
                        items(media, key = { it.identifier.value }) { item ->
                            SqueezerListGridCard(
                                media = item,
                                isSelected = item.identifier in selection,
                                onTap = {
                                    if (selection.isEmpty()) {
                                        onCompressIds(setOf(item.identifier))
                                    } else {
                                        selection = if (item.identifier in selection) {
                                            selection - item.identifier
                                        } else {
                                            selection + item.identifier
                                        }
                                    }
                                },
                                onLongPress = {
                                    selection = selection + item.identifier
                                },
                                onPreviewTap = {
                                    if (selection.isEmpty()) onPreviewMedia(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqueezerPreviewCompressionDialog(
    items: List<CompressibleMedia>,
    quality: Int,
    onCompress: (Int) -> Unit,
    onDismiss: () -> Unit,
    onViewComparison: () -> Unit,
) {
    val context = LocalContext.current
    val totalSize = items.sumOf { it.size }
    val estimatedSavings = items.sumOf { it.estimatedSavings ?: 0L }
    // Comparison only makes sense when all items share a media type — JPEG/WebP go through the
    // image pipeline; videos through frame extraction. A mixed selection has no single sample
    // representative of "what compression will look like".
    val canCompare = items.isNotEmpty() && (
        items.all { it is CompressibleImage } || items.all { it is CompressibleVideo }
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.squeezer_preview_dialog_title)) },
        text = {
            Column {
                Text(
                    text = pluralStringResource(
                        R.plurals.squeezer_preview_x_images,
                        items.size,
                        items.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.squeezer_preview_total_size),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = Formatter.formatShortFileSize(context, totalSize),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.squeezer_preview_estimated_savings),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = Formatter.formatShortFileSize(context, estimatedSavings),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCompress(quality) }) {
                Text(stringResource(R.string.squeezer_compress_action))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
                if (canCompare) {
                    TextButton(onClick = onViewComparison) {
                        Text(stringResource(R.string.squeezer_compare_action))
                    }
                }
            }
        },
    )
}

@Preview2
@Composable
private fun SqueezerListScreenEmptyPreview() {
    PreviewWrapper {
        SqueezerListScreen(
            stateSource = MutableStateFlow(SqueezerListViewModel.State(media = emptyList())),
        )
    }
}
