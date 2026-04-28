package eu.darken.sdmse.squeezer.ui.list

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ListAlt
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldPlus
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.ui.comparison.SqueezerComparisonDialog
import eu.darken.sdmse.squeezer.ui.list.items.SqueezerListGridCard
import eu.darken.sdmse.squeezer.ui.list.items.SqueezerListLinearRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private data class PendingConfirmation(
    val items: List<CompressibleImage>,
    val quality: Int,
)

private data class ComparisonRequest(
    val image: CompressibleImage,
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
        onPreviewImage = vm::openPreview,
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
                comparisonRequest = ComparisonRequest(image = sample, quality = pending.quality)
            },
        )
    }

    comparisonRequest?.let { request ->
        SqueezerComparisonDialog(
            image = request.image,
            quality = request.quality,
            onClose = { comparisonRequest = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SqueezerListScreen(
    stateSource: StateFlow<SqueezerListViewModel.State> = MutableStateFlow(SqueezerListViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onCompressAll: () -> Unit = {},
    onCompressIds: (Set<CompressibleMedia.Id>) -> Unit = {},
    onExcludeIds: (Set<CompressibleMedia.Id>) -> Unit = {},
    onToggleLayoutMode: () -> Unit = {},
    onPreviewImage: (CompressibleImage) -> Unit = {},
) {
    val context = LocalContext.current
    val state by stateSource.collectAsStateWithLifecycle()
    val images = state.images ?: emptyList()
    val itemIds = remember(images) { images.map { it.identifier }.toSet() }

    var selection by remember { mutableStateOf<Set<CompressibleMedia.Id>>(emptySet()) }

    LaunchedEffect(itemIds) {
        selection = selection intersect itemIds
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    val totalSavings = images.sumOf { it.estimatedSavings ?: 0L }
    val selectedSavings = images.filter { it.identifier in selection }.sumOf { it.estimatedSavings ?: 0L }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(CommonR.string.squeezer_tool_name))
                            if (state.progress == null && images.isNotEmpty()) {
                                val countText = pluralStringResource(
                                    CommonR.plurals.result_x_items,
                                    images.size,
                                    images.size,
                                )
                                val subtitle = if (totalSavings > 0) {
                                    "$countText • ~" + Formatter.formatShortFileSize(context, totalSavings)
                                } else {
                                    countText
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (state.progress == null && images.isNotEmpty()) {
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
                TopAppBar(
                    title = {
                        val countText = pluralStringResource(
                            CommonR.plurals.result_x_items,
                            selection.size,
                            selection.size,
                        )
                        Text(
                            text = if (selectedSavings > 0) {
                                "$countText • ~" + Formatter.formatShortFileSize(context, selectedSavings)
                            } else {
                                countText
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onCompressIds(selection) }) {
                            Icon(
                                imageVector = Icons.TwoTone.Compress,
                                contentDescription = stringResource(R.string.squeezer_compress_action),
                            )
                        }
                        IconButton(onClick = {
                            onExcludeIds(selection)
                            selection = emptySet()
                        }) {
                            Icon(
                                imageVector = SdmIcons.ShieldPlus,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                        IconButton(onClick = { selection = itemIds }) {
                            Icon(
                                imageVector = Icons.TwoTone.SelectAll,
                                contentDescription = stringResource(CommonR.string.general_list_select_all_action),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (state.progress == null && images.isNotEmpty() && selection.isEmpty()) {
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
                when (state.layoutMode) {
                    LayoutMode.LINEAR -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(images, key = { it.identifier.value }) { image ->
                            SqueezerListLinearRow(
                                image = image,
                                isSelected = image.identifier in selection,
                                onTap = {
                                    if (selection.isEmpty()) {
                                        onCompressIds(setOf(image.identifier))
                                    } else {
                                        selection = if (image.identifier in selection) {
                                            selection - image.identifier
                                        } else {
                                            selection + image.identifier
                                        }
                                    }
                                },
                                onLongPress = {
                                    selection = selection + image.identifier
                                },
                                onPreviewTap = {
                                    if (selection.isEmpty()) onPreviewImage(image)
                                },
                            )
                        }
                    }

                    LayoutMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(144.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(images, key = { it.identifier.value }) { image ->
                            SqueezerListGridCard(
                                image = image,
                                isSelected = image.identifier in selection,
                                onTap = {
                                    if (selection.isEmpty()) {
                                        onCompressIds(setOf(image.identifier))
                                    } else {
                                        selection = if (image.identifier in selection) {
                                            selection - image.identifier
                                        } else {
                                            selection + image.identifier
                                        }
                                    }
                                },
                                onLongPress = {
                                    selection = selection + image.identifier
                                },
                                onPreviewTap = {
                                    if (selection.isEmpty()) onPreviewImage(image)
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
    items: List<CompressibleImage>,
    quality: Int,
    onCompress: (Int) -> Unit,
    onDismiss: () -> Unit,
    onViewComparison: () -> Unit,
) {
    val context = LocalContext.current
    val totalSize = items.sumOf { it.size }
    val estimatedSavings = items.sumOf { it.estimatedSavings ?: 0L }

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
                if (items.isNotEmpty()) {
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
            stateSource = MutableStateFlow(SqueezerListViewModel.State(images = emptyList())),
        )
    }
}
