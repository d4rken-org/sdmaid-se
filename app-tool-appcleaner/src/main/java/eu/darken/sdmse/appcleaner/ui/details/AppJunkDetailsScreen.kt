package eu.darken.sdmse.appcleaner.ui.details

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
import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.ui.details.page.AppJunkPage
import eu.darken.sdmse.appcleaner.ui.labelRes
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
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun AppJunkDetailsScreenHost(
    vm: AppJunkDetailsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var pendingSpec by remember { mutableStateOf<DeleteSpec?>(null) }
    val viewActionLabel = stringResource(CommonR.string.general_view_action)
    val undoActionLabel = stringResource(CommonR.string.general_undo_action)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AppJunkDetailsViewModel.Event.ConfirmDelete -> pendingSpec = event.spec
                is AppJunkDetailsViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
                is AppJunkDetailsViewModel.Event.HeaderExclusionCreated -> snackScope.launch {
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
                is AppJunkDetailsViewModel.Event.SelectionExclusionsCreated -> snackScope.launch {
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

    AppJunkDetailsScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onPageChanged = vm::onPageChanged,
        onRequestDelete = vm::requestDelete,
        onExcludeJunk = vm::onExcludeJunk,
        onExcludeSelectedFiles = vm::onExcludeSelectedFiles,
        onToggleCollapse = vm::onToggleCategoryCollapse,
    )

    pendingSpec?.let { spec ->
        val message = describeSpec(spec)
        AlertDialog(
            onDismissRequest = { pendingSpec = null },
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = { Text(message) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pendingSpec = null }) {
                        Text(stringResource(CommonR.string.general_cancel_action))
                    }
                    TextButton(onClick = {
                        val captured = spec
                        pendingSpec = null
                        vm.confirmDelete(captured)
                    }) {
                        Text(stringResource(CommonR.string.general_delete_action))
                    }
                }
            },
        )
    }
}

@Composable
private fun describeSpec(spec: DeleteSpec): String {
    val context = LocalContext.current
    return when (spec) {
        is DeleteSpec.WholeJunk -> stringResource(R.string.appcleaner_delete_confirmation_message_x, spec.appLabel)
        is DeleteSpec.Inaccessible -> stringResource(
            R.string.appcleaner_delete_confirmation_message_x,
            "${spec.appLabel} (${context.getString(R.string.appcleaner_item_caches_inaccessible_title)})",
        )
        is DeleteSpec.Category -> stringResource(
            R.string.appcleaner_delete_confirmation_message_x,
            "${spec.appLabel} → ${spec.categoryLabel}",
        )
        is DeleteSpec.SingleFile -> stringResource(
            CommonR.string.general_delete_confirmation_message_x,
            spec.displayName,
        )
        is DeleteSpec.SelectedFiles -> pluralStringResource(
            R.plurals.appcleaner_delete_confirmation_message_selected_x_items,
            spec.paths.size,
            spec.paths.size,
        )
    }
}

@Composable
internal fun AppJunkDetailsScreen(
    stateSource: StateFlow<AppJunkDetailsViewModel.State> =
        MutableStateFlow(AppJunkDetailsViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onPageChanged: (InstallId) -> Unit = {},
    onRequestDelete: (DeleteSpec) -> Unit = {},
    onExcludeJunk: (InstallId) -> Unit = {},
    onExcludeSelectedFiles: (InstallId, Set<APath>) -> Unit = { _, _ -> },
    onToggleCollapse: (InstallId, ExpendablesFilterIdentifier) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val state by stateSource.collectAsStateWithLifecycle()
    val items = state.items
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { items.size })
    var selection by remember { mutableStateOf<Set<APath>>(emptySet()) }

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

    val currentJunk: AppJunk? = items.getOrNull(pagerState.currentPage)
    val livePaths = remember(currentJunk?.identifier, currentJunk?.expendables) {
        currentJunk?.expendables?.values?.flatten()?.map { it.path }?.toSet().orEmpty()
    }
    LaunchedEffect(livePaths) {
        selection = selection intersect livePaths
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(CommonR.string.appcleaner_tool_name))
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
                            val junk = currentJunk ?: return@IconButton
                            val paths = selection
                            onRequestDelete(
                                DeleteSpec.SelectedFiles(
                                    installId = junk.identifier,
                                    paths = paths,
                                ),
                            )
                        }) {
                            Icon(
                                Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_selected_action),
                            )
                        }
                        IconButton(onClick = {
                            val junk = currentJunk ?: return@IconButton
                            val paths = selection
                            selection = emptySet()
                            onExcludeSelectedFiles(junk.identifier, paths)
                        }) {
                            Icon(
                                SdmIcons.ShieldAdd,
                                contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
                            )
                        }
                        if (selection.size < livePaths.size) {
                            IconButton(onClick = { selection = livePaths }) {
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
                            items.forEachIndexed { index, junk ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    text = {
                                        Text(
                                            text = junk.label.get(context),
                                            maxLines = 1,
                                        )
                                    },
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
                                val junk = items.getOrNull(page) ?: return@HorizontalPager
                                val collapsed = state.collapsedByJunk[junk.identifier].orEmpty()
                                val isCurrentPage = junk.identifier == currentJunk?.identifier
                                AppJunkPage(
                                    junk = junk,
                                    collapsed = collapsed,
                                    selection = if (isCurrentPage) selection else emptySet(),
                                    selectionActive = isCurrentPage && selection.isNotEmpty(),
                                    onSelectionChange = { newSelection ->
                                        if (isCurrentPage) selection = newSelection
                                    },
                                    onDeleteJunk = {
                                        onRequestDelete(
                                            DeleteSpec.WholeJunk(
                                                installId = junk.identifier,
                                                appLabel = junk.label.get(context),
                                            ),
                                        )
                                    },
                                    onExcludeJunk = { onExcludeJunk(junk.identifier) },
                                    onDeleteInaccessible = {
                                        onRequestDelete(
                                            DeleteSpec.Inaccessible(
                                                installId = junk.identifier,
                                                appLabel = junk.label.get(context),
                                            ),
                                        )
                                    },
                                    onDeleteCategory = { category ->
                                        val matches = junk.expendables?.get(category).orEmpty()
                                        onRequestDelete(
                                            DeleteSpec.Category(
                                                installId = junk.identifier,
                                                category = category,
                                                matchCount = matches.size,
                                                appLabel = junk.label.get(context),
                                                categoryLabel = context.getString(category.labelRes),
                                            ),
                                        )
                                    },
                                    onDeleteFile = { category, path ->
                                        val match = junk.expendables?.get(category)
                                            ?.firstOrNull { it.path == path }
                                        onRequestDelete(
                                            DeleteSpec.SingleFile(
                                                installId = junk.identifier,
                                                category = category,
                                                path = path,
                                                displayName = match?.lookup?.userReadablePath?.get(context)
                                                    ?: path.path,
                                            ),
                                        )
                                    },
                                    onToggleCollapse = { category ->
                                        onToggleCollapse(junk.identifier, category)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AppJunkDetailsScreenEmptyPreview() {
    PreviewWrapper {
        AppJunkDetailsScreen(
            stateSource = MutableStateFlow(AppJunkDetailsViewModel.State(items = emptyList())),
        )
    }
}
