package eu.darken.sdmse.swiper.ui.swipe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.automirrored.twotone.ListAlt
import androidx.compose.material.icons.twotone.Close
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R as ExclusionR
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import eu.darken.sdmse.swiper.ui.swipe.items.LeavingCard
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperActionBar
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperDeckCard
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperLeavingCard
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperProgressPager
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperStatsCard
import eu.darken.sdmse.swiper.ui.swipe.tour.SwiperSwipeTour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SwiperSwipeScreenHost(
    route: SwiperSwipeRoute,
    vm: SwiperSwipeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val openNotSupportedText = stringResource(CommonR.string.general_error_no_compatible_app_found_msg)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SwiperSwipeViewModel.Event.TriggerHapticFeedback ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is SwiperSwipeViewModel.Event.OpenExternally -> runCatching {
                    context.startActivity(event.intent)
                }.onFailure { snackbarHostState.showSnackbar(openNotSupportedText) }
                is SwiperSwipeViewModel.Event.ShowOpenNotSupported ->
                    snackbarHostState.showSnackbar(openNotSupportedText)
            }
        }
    }

    SwiperSwipeScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onNavigateToStatus = vm::navigateToStatus,
        onSetDecision = vm::setDecision,
        onSkip = vm::skip,
        onUndo = vm::undo,
        onSetCurrentIndex = vm::setCurrentIndex,
        onOpenExternally = vm::openExternally,
        onExcludeAndRemove = vm::excludeAndRemove,
        onOpenPreview = { item ->
            vm.navTo(PreviewRoute(PreviewOptions(paths = listOf(item.lookup.lookedUp), position = 0)))
        },
    )
}

@Composable
internal fun SwiperSwipeScreen(
    stateSource: StateFlow<SwiperSwipeViewModel.State?> = MutableStateFlow(null),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onNavigateToStatus: () -> Unit = {},
    onSetDecision: (Long, SwipeDecision) -> Boolean = { _, _ -> true },
    onSkip: (Long) -> Boolean = { true },
    onUndo: () -> Unit = {},
    onSetCurrentIndex: (Int) -> Unit = {},
    onOpenExternally: (SwipeItem) -> Unit = {},
    onExcludeAndRemove: (SwipeItem) -> Unit = {},
    onOpenPreview: (SwipeItem) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    var showHelpDialog by remember { mutableStateOf(false) }
    var excludeRequest by remember { mutableStateOf<SwipeItem?>(null) }

    // Cards mid-fly-off, drawn on top of the live deck. A commit advances the deck immediately and
    // parks the outgoing card here so its fly-off animation never gates the next swipe.
    val leaving = remember { mutableStateListOf<LeavingCard>() }
    var leavingKey by remember { mutableIntStateOf(0) }

    fun commit(item: SwipeItem, outcome: SwipeOutcome, releaseX: Float, releaseY: Float) {
        val snapshot = state ?: return
        // Park the outgoing card so its fly-off plays on top of the already-promoted next card.
        fun park() = leaving.add(
            LeavingCard(
                key = leavingKey++,
                item = item,
                outcome = outcome,
                releaseX = releaseX,
                releaseY = releaseY,
                swapDirections = snapshot.swapDirections,
                showDetails = snapshot.showDetails,
                totalItems = snapshot.totalItems,
            ),
        )
        // Park only ACCEPTED commits: a rejected one (stale/double-tap guard in the VM) records
        // nothing, and playing a stamped fly-off for it would falsely confirm a decision on a
        // file-deletion surface.
        when (outcome) {
            SwipeOutcome.Keep -> if (onSetDecision(item.id, SwipeDecision.KEEP)) park()
            SwipeOutcome.Delete -> if (onSetDecision(item.id, SwipeDecision.DELETE)) park()
            SwipeOutcome.Skip -> if (onSkip(item.id)) park()
            // Undo navigates backwards — handled entirely by the VM, no fly-off overlay.
            SwipeOutcome.Undo -> onUndo()
            SwipeOutcome.SnapBack -> Unit
        }
    }

    val tourController = LocalGuidedTourController.current
    val tourDef = remember { SwiperSwipeTour.definition() }
    var tourStartAttempted by remember { mutableStateOf(false) }
    // Start once a card is in view. The tour's leading centerless step teaches the swipe gestures
    // (it replaced the old standalone first-run overlay).
    val tourReady = state?.currentItem != null
    LaunchedEffect(tourReady) {
        if (!tourReady || tourStartAttempted) return@LaunchedEffect
        // shouldStart() is false for both "done/dismissed" and "another tour active"; mark attempted
        // only after it passes so a transient block can't permanently suppress this tour.
        if (!tourController.shouldStart(tourDef)) return@LaunchedEffect
        tourStartAttempted = true
        tourController.start(tourDef)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SdmScaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.swiper_label))
                            val subtitle = state?.sessionLabel
                                ?: state?.sessionPosition?.let {
                                    stringResource(R.string.swiper_session_default_label, it)
                                }
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Close,
                            label = stringResource(CommonR.string.general_close_action),
                            onClick = onNavigateUp,
                        )
                    },
                    actions = {
                        SdmTooltipIconButton(
                            icon = Icons.AutoMirrored.TwoTone.HelpOutline,
                            label = stringResource(CommonR.string.general_help_action),
                            onClick = { showHelpDialog = true },
                        )
                        TextButton(
                            onClick = onNavigateToStatus,
                            modifier = Modifier.guidedTourTarget(SwiperSwipeTour.REVIEW_TARGET),
                        ) {
                            BadgedBox(
                                badge = {
                                    val undecided = state?.undecidedCount ?: 0
                                    if (undecided > 0) {
                                        Badge { Text(text = undecided.toString()) }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.TwoTone.ListAlt,
                                    contentDescription = null,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.swiper_review_action))
                        }
                    },
                )
            },
            bottomBar = {
                val current = state
                if (current != null) {
                    SwiperActionBar(
                        modifier = Modifier.guidedTourTarget(SwiperSwipeTour.ACTIONS_TARGET),
                        canUndo = current.canUndo,
                        swapDirections = current.swapDirections,
                        hasCurrentItem = current.currentItem != null,
                        onDelete = {
                            current.currentItem?.let { commit(it, SwipeOutcome.Delete, 0f, 0f) }
                        },
                        onKeep = {
                            current.currentItem?.let { commit(it, SwipeOutcome.Keep, 0f, 0f) }
                        },
                        onUndo = onUndo,
                        onSkip = {
                            current.currentItem?.let { commit(it, SwipeOutcome.Skip, 0f, 0f) }
                        },
                        onSkipLongPress = {
                            excludeRequest = current.currentItem
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            val current = state
            if (current == null) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@SdmScaffold
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SwiperStatsCard(state = current)
                SwiperProgressPager(
                    items = current.items,
                    currentIndex = current.currentIndex,
                    onItemClick = onSetCurrentIndex,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val front = current.currentItem
                    val back = current.nextItem?.takeIf { it.id != front?.id }
                    // Back card first (drawn behind), then the top card. Both go through one keyed
                    // call site so promoting the back card to the top MOVES its node (and its loaded
                    // preview) instead of recreating it — no re-request, no blink.
                    val deckItems = listOfNotNull(back, front)
                    deckItems.forEach { deckItem ->
                        key(deckItem.id) {
                            SwiperDeckCard(
                                item = deckItem,
                                isTop = deckItem.id == front?.id,
                                canUndo = current.canUndo,
                                swapDirections = current.swapDirections,
                                showDetails = current.showDetails,
                                sessionPosition = deckItem.itemIndex + 1,
                                totalItems = current.totalItems,
                                onCommit = { outcome, releaseX, releaseY ->
                                    commit(deckItem, outcome, releaseX, releaseY)
                                },
                                onPreviewClick = { onOpenPreview(deckItem) },
                                onOpenExternallyClick = { onOpenExternally(deckItem) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    // Outgoing cards finishing their fly-off, on top of the live deck. Non-interactive,
                    // so the freshly promoted card beneath them takes input immediately.
                    leaving.forEach { card ->
                        key(card.key) {
                            SwiperLeavingCard(
                                card = card,
                                onExitDone = { leaving.remove(card) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

    }

    if (showHelpDialog) {
        val swap = state?.swapDirections == true
        val leftAction =
            if (swap) stringResource(R.string.swiper_keep_action)
            else stringResource(CommonR.string.general_delete_action)
        val rightAction =
            if (swap) stringResource(CommonR.string.general_delete_action)
            else stringResource(R.string.swiper_keep_action)
        SdmAlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.swiper_help_title)) },
            text = { Text(stringResource(R.string.swiper_help_message, leftAction, rightAction)) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    excludeRequest?.let { item ->
        val context = LocalContext.current
        SdmConfirmDialog(
            title = stringResource(ExclusionR.string.exclusion_create_action),
            message = stringResource(
                R.string.swiper_exclude_confirmation_message,
                item.lookup.userReadablePath.get(context),
            ),
            onDismissRequest = { excludeRequest = null },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_exclude_action),
                onClick = {
                    onExcludeAndRemove(item)
                    excludeRequest = null
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { excludeRequest = null },
            ),
        )
    }
}

@Preview2
@Composable
private fun SwiperSwipeScreenPreview() {
    PreviewWrapper {
        SwiperSwipeScreen()
    }
}
