package eu.darken.sdmse.corpsefinder.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmScrollableTabStrip
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.selection.rememberSelectionState
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.corpsefinder.ui.details.content.CorpseContent
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    route: CorpseDetailsRoute,
    vm: CorpseDetailsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val undoActionLabel = stringResource(CommonR.string.general_undo_action)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is CorpseDetailsViewModel.Event.TaskResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Long,
                    )
                }
                is CorpseDetailsViewModel.Event.ExclusionsCreated -> snackScope.launch {
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
    val selection = rememberSelectionState<APath>()
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    // Drive VM page-tracking from pager state and clear selection on real page changes.
    // drop(1) skips the initial currentPage=0 emission so the scroll-to-target effect below
    // isn't clobbered by a spurious onPageChanged(items[0]) before it can scroll.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                selection.clear()
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
    val currentIds = remember(currentCorpse?.identifier, currentCorpse?.content) {
        currentCorpse?.content?.map { it.lookedUp }?.toSet().orEmpty()
    }
    LaunchedEffect(currentIds) {
        selection.retainAll(currentIds)
    }

    BackHandler(enabled = selection.isActive) { selection.clear() }

    SdmScaffold(
        topBar = {
            if (!selection.isActive) {
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
                        SdmTooltipIconButton(
                            icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                            label = stringResource(CommonR.string.general_navigate_up_action),
                            onClick = onNavigateUp,
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.count}") },
                    navigationIcon = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Close,
                            label = stringResource(CommonR.string.general_close_action),
                            onClick = { selection.clear() },
                        )
                    },
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Delete,
                            label = stringResource(CommonR.string.general_delete_selected_action),
                            onClick = {
                                val corpse = currentCorpse ?: return@SdmTooltipIconButton
                                val paths = selection.selected
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
                            },
                        )
                        if (selection.count < currentIds.size) {
                            SdmTooltipIconButton(
                                icon = Icons.TwoTone.SelectAll,
                                label = stringResource(CommonR.string.general_list_select_all_action),
                                onClick = { selection.setSelection(currentIds) },
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
                        SdmScrollableTabStrip(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, items.lastIndex),
                            tabCount = items.size,
                            onTabSelected = { index ->
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                        ) { index ->
                            Text(items[index].lookup.name)
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
                                    selection = if (corpse.identifier == currentCorpse?.identifier) selection else null,
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

        SdmConfirmDialog(
            title = stringResource(CommonR.string.general_delete_confirmation_title),
            message = message,
            onDismissRequest = { pendingDelete = null },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_delete_action),
                onClick = {
                    val corpseId = pending.corpseId
                    val paths = pending.paths
                    pendingDelete = null
                    selection.clear()
                    if (paths == null) {
                        onDeleteCorpse(corpseId)
                    } else {
                        onDeleteContent(corpseId, paths)
                    }
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { pendingDelete = null },
            ),
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

@Preview2
@Composable
private fun CorpseDetailsScreenPreview() {
    PreviewWrapper {
        CorpseDetailsScreen(
            stateSource = MutableStateFlow(
                CorpseDetailsViewModel.State(
                    items = listOf(
                        previewCorpse(riskLevel = RiskLevel.KEEPER),
                        previewCorpse(riskLevel = RiskLevel.COMMON),
                    ),
                ),
            ),
        )
    }
}
