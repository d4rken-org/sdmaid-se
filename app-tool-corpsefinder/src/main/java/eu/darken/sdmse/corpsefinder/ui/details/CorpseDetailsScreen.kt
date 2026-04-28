package eu.darken.sdmse.corpsefinder.ui.details

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.ui.details.content.CorpseContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Snapshot of a pending delete request the user needs to confirm.
 * `paths == null` means "delete the whole corpse" (header button); otherwise delete [paths].
 */
internal data class PendingDelete(
    val corpseId: CorpseIdentifier,
    val paths: Set<APath>?,
    val singleName: String?,
)

@Composable
fun CorpseDetailsScreenHost(
    vm: CorpseDetailsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is CorpseDetailsViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    CorpseDetailsScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onPageChanged = vm::onPageChanged,
        onDeleteCorpse = vm::onConfirmDeleteCorpse,
        onDeleteContent = vm::onConfirmDeleteContent,
        onExcludeCorpse = vm::onExcludeCorpse,
    )
}

@Composable
internal fun CorpseDetailsScreen(
    stateSource: StateFlow<CorpseDetailsViewModel.State> = MutableStateFlow(CorpseDetailsViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onPageChanged: (CorpseIdentifier) -> Unit = {},
    onDeleteCorpse: (CorpseIdentifier) -> Unit = {},
    onDeleteContent: (CorpseIdentifier, Set<APath>) -> Unit = { _, _ -> },
    onExcludeCorpse: (CorpseIdentifier) -> Unit = {},
) {
    val context = LocalContext.current
    val state by stateSource.collectAsStateWithLifecycle()
    val items = state.items
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { items.size })
    var selection by remember { mutableStateOf<Set<APath>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    // Drive VM page-tracking from pager state and clear selection on real page changes.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selection = emptySet()
                items.getOrNull(page)?.identifier?.let(onPageChanged)
            }
    }

    // Restore pager position when target changes (e.g. deep-linked corpsePath or data reshuffle).
    LaunchedEffect(state.target, items) {
        val target = state.target ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.identifier == target }
        if (idx != -1 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    // Intersect selection with what still exists on the current page.
    val currentCorpse = items.getOrNull(pagerState.currentPage)
    LaunchedEffect(currentCorpse?.identifier, currentCorpse?.content?.map { it.lookedUp }?.toSet()) {
        val currentIds = currentCorpse?.content?.map { it.lookedUp }?.toSet() ?: emptySet()
        selection = selection intersect currentIds
    }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(CommonR.string.corpsefinder_tool_name))
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
                            val corpse = currentCorpse ?: return@IconButton
                            val paths = selection
                            val name = if (paths.size == 1) {
                                corpse.content
                                    .firstOrNull { it.lookedUp == paths.first() }
                                    ?.userReadablePath?.get(context)
                            } else {
                                null
                            }
                            pendingDelete = PendingDelete(
                                corpseId = corpse.identifier,
                                paths = paths,
                                singleName = name,
                            )
                        }) {
                            Icon(
                                Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_delete_selected_action),
                            )
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
                            items.forEachIndexed { index, corpse ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    text = { Text(corpse.lookup.name) },
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
                                val corpse = items.getOrNull(page) ?: return@HorizontalPager
                                CorpseContent(
                                    corpse = corpse,
                                    selection = if (corpse.identifier == currentCorpse?.identifier) selection else emptySet(),
                                    onSelectionChange = { newSelection ->
                                        if (corpse.identifier == currentCorpse?.identifier) selection = newSelection
                                    },
                                    onDeleteCorpseRequest = {
                                        pendingDelete = PendingDelete(
                                            corpseId = corpse.identifier,
                                            paths = null,
                                            singleName = corpse.lookup.userReadableName.get(context),
                                        )
                                    },
                                    onExcludeRequest = { onExcludeCorpse(corpse.identifier) },
                                    onFileTap = { lookup ->
                                        pendingDelete = PendingDelete(
                                            corpseId = corpse.identifier,
                                            paths = setOf(lookup.lookedUp),
                                            singleName = lookup.userReadablePath.get(context),
                                        )
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
                        val corpseId = pending.corpseId
                        val paths = pending.paths
                        pendingDelete = null
                        selection = emptySet()
                        if (paths == null) {
                            onDeleteCorpse(corpseId)
                        } else {
                            onDeleteContent(corpseId, paths)
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
private fun CorpseDetailsScreenEmptyPreview() {
    PreviewWrapper {
        CorpseDetailsScreen(
            stateSource = MutableStateFlow(CorpseDetailsViewModel.State(items = emptyList())),
        )
    }
}
