package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Filter
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.twotone.SwipeRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.storage.SystemStorageScanner
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ContentScreenHost(
    route: ContentRoute,
    vm: ContentViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var showOtherDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ContentViewModel.Event.ShowNoAccessHint -> {
                    if (event.item.path == SystemStorageScanner.OTHER_PATH) {
                        showOtherDialog = true
                    } else {
                        snackScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.analyzer_content_access_opaque),
                            )
                        }
                    }
                }
                is ContentViewModel.Event.ExclusionsCreated -> snackScope.launch {
                    val message = context.resources.getQuantityString(
                        CommonR.plurals.exclusion_x_new_exclusions,
                        event.items.size,
                        event.items.size,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(CommonR.string.general_view_action),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        if (event.items.size == 1) {
                            vm.onOpenExclusion(event.items.single())
                        } else {
                            vm.navTo(ExclusionsListRoute)
                        }
                    }
                }
                is ContentViewModel.Event.ContentDeleted -> snackScope.launch {
                    val itemText = context.resources.getQuantityString(
                        CommonR.plurals.general_delete_success_deleted_x,
                        event.count,
                        event.count,
                    )
                    val (spaceFormatted, spaceQuantity) = ByteFormatter.formatSize(context, event.freedSpace)
                    val spaceText = context.resources.getQuantityString(
                        CommonR.plurals.general_result_x_space_freed,
                        spaceQuantity,
                        spaceFormatted,
                    )
                    snackbarHostState.showSnackbar(message = "$itemText $spaceText")
                }
                is ContentViewModel.Event.OpenContent -> {
                    runCatching { context.startActivity(event.intent) }
                        .onFailure { error ->
                            if (error is ActivityNotFoundException) {
                                snackScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(CommonR.string.general_error_no_compatible_app_found_msg),
                                    )
                                }
                            }
                        }
                }
                is ContentViewModel.Event.SwiperSessionCreated -> snackScope.launch {
                    val msg = context.resources.getQuantityString(
                        R.plurals.analyzer_content_swiper_session_created_x_items,
                        event.itemCount,
                        event.itemCount,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = context.getString(CommonR.string.general_view_action),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.navTo(SwiperSessionsRoute)
                    }
                }
            }
        }
    }

    ContentScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onItemClick = vm::onItemClick,
        onDeleteSelected = vm::onDeleteSelected,
        onExcludeSelected = vm::onExcludeSelected,
        onCreateFilter = vm::onCreateFilter,
        onCreateSwiperSession = vm::onCreateSwiperSession,
        onLayoutModeToggle = vm::onLayoutModeToggle,
        onNavigateBack = vm::onNavigateBack,
    )

    if (showOtherDialog) {
        AlertDialog(
            onDismissRequest = { showOtherDialog = false },
            title = { Text(stringResource(R.string.analyzer_storage_content_type_system_other_label)) },
            text = { Text(stringResource(R.string.analyzer_storage_content_type_system_other_desc)) },
            confirmButton = {
                TextButton(onClick = { showOtherDialog = false }) {
                    Text(stringResource(CommonR.string.general_dismiss_action))
                }
            },
        )
    }
}

@Composable
internal fun ContentScreen(
    stateSource: Flow<ContentViewModel.State> = MutableStateFlow(ContentViewModel.State.Loading),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onItemClick: (ContentViewModel.Item) -> Unit = {},
    onDeleteSelected: (Set<ContentItem>) -> Unit = {},
    onExcludeSelected: (Set<ContentItem>) -> Unit = {},
    onCreateFilter: (Set<ContentItem>) -> Unit = {},
    onCreateSwiperSession: (Set<ContentItem>) -> Unit = {},
    onLayoutModeToggle: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = ContentViewModel.State.Loading)
    val context = LocalContext.current

    var selection by remember { mutableStateOf<Set<APath>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<Set<ContentItem>?>(null) }

    BackHandler(enabled = true) {
        if (selection.isNotEmpty()) {
            selection = emptySet()
        } else {
            onNavigateBack()
        }
    }

    when (val s = state) {
        ContentViewModel.State.NotFound -> {
            LaunchedEffect(Unit) { onNavigateBack() }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                },
            ) { _ -> }
        }

        ContentViewModel.State.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                ProgressOverlay(
                    data = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) { }
            }
        }

        is ContentViewModel.State.Ready -> {
            // Drop stale selection paths after upstream reshuffle.
            val livePaths = remember(s.items) { s.items.orEmpty().map { it.content.path }.toSet() }
            LaunchedEffect(livePaths) {
                val intersected = selection.intersect(livePaths)
                if (intersected.size != selection.size) selection = intersected
            }

            val selectedItems: Set<ContentItem> = remember(selection, s.items) {
                s.items.orEmpty()
                    .filter { it.content.path in selection }
                    .map { it.content }
                    .toSet()
            }
            val isSelectionMode = selection.isNotEmpty()
            val noneInaccessible = selectedItems.none { it.inaccessible }

            val spanCount = if (s.layoutMode == LayoutMode.GRID) {
                max(context.getSpanCount(widthDp = 144), 3)
            } else 1

            Scaffold(
                topBar = {
                    if (isSelectionMode) {
                        TopAppBar(
                            title = {
                                Text(
                                    pluralStringResource(
                                        CommonR.plurals.result_x_items,
                                        selection.size,
                                        selection.size,
                                    ),
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { selection = emptySet() }) {
                                    Icon(Icons.TwoTone.Close, contentDescription = null)
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    val all = s.items.orEmpty()
                                        .map { it.content.path }
                                        .toSet()
                                    selection = if (selection == all) emptySet() else all
                                }) {
                                    Icon(Icons.TwoTone.SelectAll, contentDescription = null)
                                }
                                if (!s.isReadOnly && noneInaccessible) {
                                    IconButton(onClick = { pendingDelete = selectedItems }) {
                                        Icon(Icons.TwoTone.Delete, contentDescription = null)
                                    }
                                }
                                IconButton(onClick = {
                                    onExcludeSelected(selectedItems)
                                    selection = emptySet()
                                }) {
                                    Icon(Icons.TwoTone.Block, contentDescription = null)
                                }
                                if (!s.isReadOnly && noneInaccessible) {
                                    IconButton(onClick = {
                                        onCreateFilter(selectedItems)
                                        selection = emptySet()
                                    }) {
                                        Icon(Icons.TwoTone.Filter, contentDescription = null)
                                    }
                                    IconButton(onClick = {
                                        onCreateSwiperSession(selectedItems)
                                        selection = emptySet()
                                    }) {
                                        Icon(
                                            Icons.TwoTone.SwipeRight,
                                            contentDescription = stringResource(R.string.analyzer_content_create_swiper_session_action),
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        TopAppBar(
                            title = {
                                Column {
                                    s.title?.let {
                                        Text(it.get(context))
                                    }
                                    s.subtitle?.let {
                                        Text(
                                            text = it.get(context),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                                }
                            },
                            actions = {
                                IconButton(onClick = onLayoutModeToggle) {
                                    Icon(
                                        imageVector = when (s.layoutMode) {
                                            LayoutMode.LINEAR -> Icons.TwoTone.GridView
                                            LayoutMode.GRID -> Icons.AutoMirrored.TwoTone.ViewList
                                        },
                                        contentDescription = stringResource(CommonR.string.general_toggle_layout_mode),
                                    )
                                }
                            },
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                ProgressOverlay(
                    data = s.progress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    if (s.progress == null && s.items != null) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(spanCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (s.showSystemInfoBanner) {
                                item(
                                    key = "info-banner",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    ContentInfoBanner()
                                }
                            }
                            items(s.items, s.layoutMode, selection, isSelectionMode, onItemClick) { itemPath ->
                                selection = if (itemPath in selection) selection - itemPath else selection + itemPath
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { items ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = {
                Text(
                    pluralStringResource(
                        CommonR.plurals.general_delete_confirmation_message_selected_x_items,
                        items.size,
                        items.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSelected(items)
                    pendingDelete = null
                    selection = emptySet()
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.items(
    items: List<ContentViewModel.Item>,
    layoutMode: LayoutMode,
    selection: Set<APath>,
    isSelectionMode: Boolean,
    onItemClick: (ContentViewModel.Item) -> Unit,
    onToggleSelection: (APath) -> Unit,
) {
    items(
        count = items.size,
        key = { idx -> items[idx].content.path.path.hashCode() },
    ) { idx ->
        val item = items[idx]
        val isSelected = item.content.path in selection
        when (layoutMode) {
            LayoutMode.LINEAR -> ContentItemRow(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onTap = {
                    if (isSelectionMode) {
                        onToggleSelection(item.content.path)
                    } else {
                        onItemClick(item)
                    }
                },
                onLongPress = { onToggleSelection(item.content.path) },
            )

            LayoutMode.GRID -> ContentItemTile(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onTap = {
                    if (isSelectionMode) {
                        onToggleSelection(item.content.path)
                    } else {
                        onItemClick(item)
                    }
                },
                onLongPress = { onToggleSelection(item.content.path) },
            )
        }
    }
}

@Preview2
@Composable
private fun ContentScreenLoadingPreview() {
    PreviewWrapper {
        ContentScreen()
    }
}
