package eu.darken.sdmse.systemcleaner.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.snapshotFlow
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
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.ui.details.page.FilterContentPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal data class PendingFilterDelete(
    val filterId: FilterIdentifier,
    val paths: Set<APath>?,
    val singleName: String?,
)

@Composable
fun FilterContentDetailsScreenHost(
    vm: FilterContentDetailsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val undoActionLabel = stringResource(CommonR.string.general_undo_action)
    val viewActionLabel = stringResource(CommonR.string.general_view_action)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is FilterContentDetailsViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
                is FilterContentDetailsViewModel.Event.ExclusionsCreated -> snackScope.launch {
                    val message = context.resources.getQuantityString(
                        CommonR.plurals.exclusion_x_new_exclusions,
                        event.count,
                        event.count,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.onUndoExclude(event.undo, event.restoreTarget)
                    }
                }
                is FilterContentDetailsViewModel.Event.SelectionExclusionsCreated -> snackScope.launch {
                    val message = context.resources.getQuantityString(
                        CommonR.plurals.exclusion_x_new_exclusions,
                        event.count,
                        event.count,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = viewActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.onShowExclusions()
                    }
                }
            }
        }
    }

    FilterContentDetailsScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onPageChanged = vm::onPageChanged,
        onDeleteFilter = vm::onConfirmDeleteFilter,
        onDeleteFiles = vm::onConfirmDeleteFiles,
        onExcludeFilter = vm::onExcludeFilter,
        onExcludeFiles = vm::onExcludeFiles,
        onPreviewFile = vm::onPreviewFile,
    )
}

@Composable
internal fun FilterContentDetailsScreen(
    stateSource: StateFlow<FilterContentDetailsViewModel.State> =
        MutableStateFlow(FilterContentDetailsViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onPageChanged: (FilterIdentifier) -> Unit = {},
    onDeleteFilter: (FilterIdentifier) -> Unit = {},
    onDeleteFiles: (FilterIdentifier, Set<APath>) -> Unit = { _, _ -> },
    onExcludeFilter: (FilterIdentifier) -> Unit = {},
    onExcludeFiles: (FilterIdentifier, Set<APath>) -> Unit = { _, _ -> },
    onPreviewFile: (FilterIdentifier, APath) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val state by stateSource.collectAsStateWithLifecycle()
    val items = state.items
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { items.size })
    var selection by remember { mutableStateOf<Set<APath>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<PendingFilterDelete?>(null) }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selection = emptySet()
                items.getOrNull(page)?.identifier?.let(onPageChanged)
            }
    }

    LaunchedEffect(state.target, items) {
        val target = state.target ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.identifier == target }
        if (idx != -1 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    val currentFilter = items.getOrNull(pagerState.currentPage)
    val currentPaths = remember(currentFilter?.identifier, currentFilter?.items) {
        currentFilter?.items?.map { it.path }?.toSet().orEmpty()
    }
    LaunchedEffect(currentPaths) {
        selection = selection intersect currentPaths
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(CommonR.string.systemcleaner_tool_name))
                            Text(
                                text = stringResource(CommonR.string.general_details_label),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
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
                            val filter = currentFilter ?: return@IconButton
                            val paths = selection
                            val name = if (paths.size == 1) {
                                filter.items.firstOrNull { it.path == paths.first() }
                                    ?.lookup?.userReadablePath?.get(context)
                            } else {
                                null
                            }
                            pendingDelete = PendingFilterDelete(
                                filterId = filter.identifier,
                                paths = paths,
                                singleName = name,
                            )
                        }) {
                            Icon(
                                Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_selected_action),
                            )
                        }
                        IconButton(onClick = {
                            val filter = currentFilter ?: return@IconButton
                            val paths = selection
                            selection = emptySet()
                            onExcludeFiles(filter.identifier, paths)
                        }) {
                            Icon(
                                SdmIcons.ShieldAdd,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                        if (selection.size < currentPaths.size) {
                            IconButton(onClick = { selection = currentPaths }) {
                                Icon(
                                    Icons.TwoTone.SelectAll,
                                    contentDescription = stringResource(CommonR.string.general_list_select_all_action),
                                )
                            }
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
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(CommonR.string.general_empty_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, items.lastIndex),
                            edgePadding = 0.dp,
                        ) {
                            items.forEachIndexed { index, filterContent ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    text = { Text(filterContent.label.get(context)) },
                                )
                            }
                        }
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val spanCount = remember(maxWidth) {
                                context.getSpanCount(widthDp = 390)
                            }
                            val pageWidth = maxWidth / spanCount
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                pageSize = PageSize.Fixed(pageWidth),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                verticalAlignment = Alignment.Top,
                            ) { page ->
                                val filterContent = items.getOrNull(page) ?: return@HorizontalPager
                                FilterContentPage(
                                    filterContent = filterContent,
                                    selection = if (filterContent.identifier == currentFilter?.identifier) {
                                        selection
                                    } else {
                                        emptySet()
                                    },
                                    onSelectionChange = { newSelection ->
                                        if (filterContent.identifier == currentFilter?.identifier) {
                                            selection = newSelection
                                        }
                                    },
                                    onDeleteFilterRequest = {
                                        pendingDelete = PendingFilterDelete(
                                            filterId = filterContent.identifier,
                                            paths = null,
                                            singleName = filterContent.label.get(context),
                                        )
                                    },
                                    onExcludeFilterRequest = { onExcludeFilter(filterContent.identifier) },
                                    onFileTap = { fileRow ->
                                        pendingDelete = PendingFilterDelete(
                                            filterId = filterContent.identifier,
                                            paths = setOf(fileRow.match.path),
                                            singleName = fileRow.match.lookup.userReadablePath.get(context),
                                        )
                                    },
                                    onPreviewFile = { path ->
                                        onPreviewFile(filterContent.identifier, path)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        val message = when {
            pending.singleName != null -> stringResource(
                CommonR.string.general_delete_confirmation_message_x,
                pending.singleName,
            )

            else -> pluralStringResource(
                CommonR.plurals.general_delete_confirmation_message_selected_x_items,
                pending.paths?.size ?: 1,
                pending.paths?.size ?: 1,
            )
        }

        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = { Text(message) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(CommonR.string.general_cancel_action))
                    }
                    TextButton(onClick = {
                        val filterId = pending.filterId
                        val paths = pending.paths
                        pendingDelete = null
                        selection = emptySet()
                        if (paths == null) {
                            onDeleteFilter(filterId)
                        } else {
                            onDeleteFiles(filterId, paths)
                        }
                    }) {
                        Text(stringResource(CommonR.string.general_delete_action))
                    }
                }
            },
        )
    }
}

@Preview2
@Composable
private fun FilterContentDetailsScreenEmptyPreview() {
    PreviewWrapper {
        FilterContentDetailsScreen(
            stateSource = MutableStateFlow(FilterContentDetailsViewModel.State(items = emptyList())),
        )
    }
}
