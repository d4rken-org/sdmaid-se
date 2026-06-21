package eu.darken.sdmse.main.ui.dashboard

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.bottom.BottomBar
import eu.darken.sdmse.main.ui.dashboard.cards.DashboardItem
import eu.darken.sdmse.main.ui.dashboard.cards.DashboardListCard
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SwiperDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.tour.DashboardTour
import eu.darken.sdmse.main.ui.navigation.SettingsRoute
import eu.darken.sdmse.main.ui.settings.general.OneClickOptionsDialog
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import kotlin.math.abs

@Composable
fun DashboardScreenHost(
    vm: DashboardViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val activity = LocalContext.current as? Activity ?: return
    val snackbarHostState = remember { SnackbarHostState() }

    val listState by vm.listState.collectAsStateWithLifecycle()
    val bottomBarState by vm.bottomBarState.collectAsStateWithLifecycle()
    val oneClickOptionsState by vm.oneClickOptionsState.collectAsStateWithLifecycle()
    val isHeroDismissed by vm.isHeroDismissed.collectAsStateWithLifecycle()

    var dialogState by remember { mutableStateOf<DashboardDialogState?>(null) }

    LaunchedEffect(vm, activity, snackbarHostState) {
        vm.events.collect { event ->
            when (event) {
                is DashboardEvents.CorpseFinderDeleteConfirmation -> {
                    dialogState = DashboardDialogState.CorpseFinderDelete
                }

                is DashboardEvents.SystemCleanerDeleteConfirmation -> {
                    dialogState = DashboardDialogState.SystemCleanerDelete
                }

                is DashboardEvents.AppCleanerDeleteConfirmation -> {
                    dialogState = DashboardDialogState.AppCleanerDelete
                }

                is DashboardEvents.DeduplicatorDeleteConfirmation -> {
                    dialogState = DashboardDialogState.DeduplicatorDelete(
                        clusters = event.clusters ?: emptyList(),
                    )
                }

                DashboardEvents.SetupDismissHint -> {
                    val result = snackbarHostState.showSnackbar(
                        message = activity.getString(R.string.setup_dismiss_hint),
                        actionLabel = activity.getString(CommonR.string.general_undo_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoSetupHide()
                    }
                }

                is DashboardEvents.TaskResult -> Unit

                DashboardEvents.TodoHint -> {
                    dialogState = DashboardDialogState.Todo
                }

                is DashboardEvents.OpenIntent -> {
                    try {
                        activity.startActivity(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        vm.errorEvents.emit(e)
                    }
                }

                DashboardEvents.SqueezerSetup -> vm.navTo(SqueezerSetupRoute)

                DashboardEvents.ShowShortRecordingWarning -> {
                    dialogState = DashboardDialogState.ShortRecordingWarning
                }

                is DashboardEvents.ShowUnknownFolders -> {
                    dialogState = DashboardDialogState.UnknownFolders(
                        scannedCount = event.scannedCount,
                        skippedCount = event.skippedCount,
                        unknownPaths = event.unknownPaths,
                    )
                }
            }
        }
    }

    DashboardEventDialogs(
        state = dialogState,
        onDismiss = { dialogState = null },
        onConfirmCorpseFinder = vm::confirmCorpseDeletion,
        onShowCorpseFinder = vm::showCorpseFinder,
        onConfirmSystemCleaner = vm::confirmFilterContentDeletion,
        onShowSystemCleaner = vm::showSystemCleaner,
        onConfirmAppCleaner = vm::confirmAppJunkDeletion,
        onShowAppCleaner = vm::showAppCleaner,
        onConfirmDeduplicator = vm::confirmDeduplicatorDeletion,
        onShowDeduplicator = vm::showDeduplicator,
        onPreviewDeduplicator = { options -> vm.navTo(eu.darken.sdmse.common.previews.PreviewRoute(options = options)) },
        onStopShortRecording = vm::confirmStopRecording,
        onConfirmMainAction = vm::mainAction,
    )

    DashboardScreen(
        listState = listState,
        bottomBarState = bottomBarState,
        isTv = vm.isTvDevice,
        oneClickOptionsState = oneClickOptionsState,
        isHeroDismissed = isHeroDismissed,
        snackbarHostState = snackbarHostState,
        onDismissHero = vm::dismissHero,
        onMainAction = {
            when (val actionState = bottomBarState?.actionState ?: return@DashboardScreen) {
                BottomBarState.Action.DELETE -> {
                    dialogState = DashboardDialogState.MainActionDelete(actionState)
                }

                else -> vm.mainAction(actionState)
            }
        },
        onToolClick = vm::onHeroToolClick,
        onRestoreHero = vm::restoreHero,
        onDiscardResults = vm::discardResults,
        onSettings = { vm.navTo(SettingsRoute) },
        onUpgrade = { vm.navTo(UpgradeRoute()) },
        onCorpseFinderOneClickChanged = vm::setCorpseFinderOneClickEnabled,
        onSystemCleanerOneClickChanged = vm::setSystemCleanerOneClickEnabled,
        onAppCleanerOneClickChanged = vm::setAppCleanerOneClickEnabled,
        onDeduplicatorOneClickChanged = vm::setDeduplicatorOneClickEnabled,
    )
}

@Composable
internal fun DashboardScreen(
    listState: DashboardViewModel.ListState? = null,
    bottomBarState: BottomBarState? = null,
    isTv: Boolean = false,
    oneClickOptionsState: OneClickOptionsState = OneClickOptionsState(),
    isHeroDismissed: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onMainAction: () -> Unit = {},
    onToolClick: (HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
    onRestoreHero: () -> Unit = {},
    onDiscardResults: () -> Unit = {},
    onSettings: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onDismissHero: () -> Unit = {},
    onCorpseFinderOneClickChanged: (Boolean) -> Unit = {},
    onSystemCleanerOneClickChanged: (Boolean) -> Unit = {},
    onAppCleanerOneClickChanged: (Boolean) -> Unit = {},
    onDeduplicatorOneClickChanged: (Boolean) -> Unit = {},
) {
    var showOneClickOptions by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    val tourController = LocalGuidedTourController.current
    // Tour-aware screens watch this and freeze any transient dock (auto-hiding bottom bar,
    // dismiss-on-scroll banners, etc.) so tour targets stay visible when prepareTarget
    // scrolls or when steps cycle through.
    val tourSession by tourController.session.collectAsStateWithLifecycle()
    // Narrow to *this* screen's tour. A global tour spawned from elsewhere shouldn't be allowed
    // to inflate dashboard bottom padding or otherwise change layout here.
    val dashboardTourActive = tourSession?.definition?.id == DashboardTour.id

    // Auto-hide the bottom bar on scroll, BUT pin it visible whenever a tour is active or this is
    // a TV-style device: D-pad navigation scrolls the grid via bringIntoView, which would trip the
    // hide heuristic — merely navigating down the list would hide the controls being navigated to.
    // Re-keying on dockPinned cancels the scroll listener while pinned.
    val dockPinned = isTv || dashboardTourActive
    LaunchedEffect(gridState, dockPinned) {
        if (dockPinned) {
            isBottomBarVisible = true
            return@LaunchedEffect
        }
        var previousPos = 0
        snapshotFlow { gridState.firstVisibleItemIndex * 10_000 + gridState.firstVisibleItemScrollOffset }
            .collect { currentPos ->
                when {
                    currentPos <= 0 -> isBottomBarVisible = true
                    abs(currentPos - previousPos) > 6 -> isBottomBarVisible = currentPos < previousPos
                }
                previousPos = currentPos
            }
    }
    // Effective visibility: pinning wins even before the effect above runs (e.g. a restored
    // hidden state on a TV) so animation and focus gating never disagree with the pin.
    val dockVisible = dockPinned || isBottomBarVisible

    val items = listState?.items
    val hasSetup = remember(items) {
        items?.any { it is SetupDashboardCardItem } == true
    }
    val hasSwiper = remember(items) {
        items?.any { it is SwiperDashboardCardItem } == true
    }
    val firstToolIndex = remember(items) {
        items?.indexOfFirst { it is ToolDashboardCardItem }?.takeIf { it >= 0 }
    }
    // Prepare callbacks resolve indices at *invocation* time rather than capturing them when
    // the tour starts. Otherwise an item-list refresh mid-tour (a new card appears or order
    // shifts) could leave the lambdas scrolling to a stale index.
    val itemsState = rememberUpdatedState(items)
    val tourDef = remember(gridState, hasSetup, hasSwiper) {
        DashboardTour.definition(
            includeSetup = hasSetup,
            includeManualTool = hasSwiper,
            // Scroll the LazyVerticalGrid to each step's target before it renders: with a tall
            // setup card or compact device, the target card can be off-screen and otherwise
            // wouldn't register a target rect (LazyGrid composes only visible items).
            prepareSetup = {
                scrollToFirstMatching(gridState, itemsState.value) { it is SetupDashboardCardItem }
            },
            prepareTools = {
                scrollToFirstMatching(gridState, itemsState.value) { it is ToolDashboardCardItem }
            },
            prepareManualTool = {
                scrollToFirstMatching(gridState, itemsState.value) { it is SwiperDashboardCardItem }
            },
        )
    }
    // Don't start the tour until at least one tool card exists: the tools step targets the
    // first ToolDashboardCardItem and would otherwise grace-skip, leaving the overview followed
    // by a visibly missing anchor step.
    LaunchedEffect(bottomBarState?.isReady, firstToolIndex) {
        if (bottomBarState?.isReady != true) return@LaunchedEffect
        if (firstToolIndex == null) return@LaunchedEffect
        if (!tourController.shouldStart(tourDef)) return@LaunchedEffect
        gridState.animateScrollToItem(0)
        tourController.start(tourDef)
    }

    if (showOneClickOptions) {
        OneClickOptionsDialog(
            corpseFinderEnabled = oneClickOptionsState.corpseFinderEnabled,
            systemCleanerEnabled = oneClickOptionsState.systemCleanerEnabled,
            appCleanerEnabled = oneClickOptionsState.appCleanerEnabled,
            deduplicatorEnabled = oneClickOptionsState.deduplicatorEnabled,
            onCorpseFinderChanged = onCorpseFinderOneClickChanged,
            onSystemCleanerChanged = onSystemCleanerOneClickChanged,
            onAppCleanerChanged = onAppCleanerOneClickChanged,
            onDeduplicatorChanged = onDeduplicatorOneClickChanged,
            onDismiss = { showOneClickOptions = false },
        )
    }

    // D-pad bridges between the grid and the bottom dock. The grid is a fillMaxSize focus group
    // that fully contains the dock's rects, so neither side reliably qualifies as a directional
    // candidate of the other — all crossings are made explicit via these requesters: the grid's
    // key handler jumps to the dock at grid edges, the dock's key handler returns to the grid
    // (restoring the remembered card) on UP or at dock edges.
    val gridFocusRequester = remember { FocusRequester() }
    val dockFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Focus memory for dock→grid returns, tracked explicitly: each grid item carries its own
    // FocusRequester and reports gaining focus via onFocusEvent. The framework alternatives
    // don't deliver here: Modifier.focusRestorer() never fires for requester-based or spatial
    // entries, and FocusRequester.saveFocusedChild()/restoreFocusedChild() saves but fails to
    // restore for this hierarchy (hash lookup misses even when called back-to-back).
    val cardFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var lastFocusedCardId by remember { mutableStateOf<Long?>(null) }
    // Restore the card the user left from; fresh entries (nothing tracked yet) or a card that
    // left composition (requestFocus throws on an unattached requester) fall back to the
    // default group enter.
    val returnToGrid: () -> Unit = {
        val restored = lastFocusedCardId
            ?.let { cardFocusRequesters[it] }
            ?.let { runCatching { it.requestFocus() }.isSuccess }
            ?: false
        if (!restored) gridFocusRequester.requestFocus()
    }

    // Suppress the hero while a tour is active so it can't cover or fight tour targets.
    val heroVisible = bottomBarState?.heroSummary != null && !isHeroDismissed && !dashboardTourActive

    // Dismissing or discarding the hero with DPAD_CENTER removes the focused node from
    // composition and focus evaporates — the next key press would restart from the default.
    // Tracked at the screen root because the hero's own focus-event node leaves composition
    // together with the focused control and can't report the loss itself. Re-anchor on the dock.
    var screenHasFocus by remember { mutableStateOf(false) }
    var wasHeroVisible by remember { mutableStateOf(heroVisible) }
    LaunchedEffect(heroVisible) {
        val heroJustLeft = wasHeroVisible && !heroVisible
        wasHeroVisible = heroVisible
        if (heroJustLeft && !screenHasFocus) {
            runCatching { dockFocusRequester.requestFocus() }
        }
    }

    Scaffold(
        modifier = Modifier.onFocusEvent { screenHasFocus = it.hasFocus },
        bottomBar = {
            BottomBar(
                // Focus group so the grid's UP wrap can target "the dock" as a whole: the
                // requester enters the group and lands on whichever control is actually present
                // (the FAB is absent before ready and inert while WORKING).
                modifier = Modifier
                    .focusRequester(dockFocusRequester)
                    // Directional moves that would leave the dock spatially (overlapping grid
                    // cards can qualify as "left of the FAB") are cancelled; the onKeyEvent below
                    // then sees the failed move and performs the restoring return instead. Only
                    // while the grid actually has cards — with a loading/empty grid the dock must
                    // not trap focus (the onKeyEvent below leaves those keys unconsumed too).
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection != FocusDirection.Enter && items?.isNotEmpty() == true) {
                                cancelFocusChange()
                            }
                        }
                    }
                    .focusGroup()
                    // The single return path from the dock to the grid. DOWN and LEFT/RIGHT
                    // first move within the dock and return once they hit its edge — left-left
                    // bounces between a list row and the bar. UP is hero-gated: with the hero
                    // shown it climbs the dock spatially first (bar → Discard → Dismiss → grid),
                    // making the hero's top-right controls reachable; without a hero it stays
                    // the instant one-press escape (the visible state justifies the modal
                    // behavior — an extra FAB hop for geometric purity would not). Every return
                    // restores the remembered card via returnToGrid.
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val wantsReturn = when (event.key) {
                            Key.DirectionUp ->
                                if (heroVisible) !focusManager.moveFocus(FocusDirection.Up) else true
                            Key.DirectionDown -> !focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionLeft -> !focusManager.moveFocus(FocusDirection.Left)
                            Key.DirectionRight -> !focusManager.moveFocus(FocusDirection.Right)
                            else -> return@onKeyEvent false
                        }
                        when {
                            !wantsReturn -> true
                            // Grid has cards to return to: restore the remembered card and consume.
                            items?.isNotEmpty() == true -> {
                                returnToGrid()
                                true
                            }
                            // Loading/empty grid: don't swallow the key — fall through to default
                            // geometric focus search so the dock never becomes a focus trap while
                            // listState is null (spinner) or the list is empty.
                            else -> false
                        }
                    },
                state = bottomBarState,
                isVisible = dockVisible,
                heroVisible = heroVisible,
                onMainAction = onMainAction,
                onMainActionLongClick = { showOneClickOptions = true },
                onSettings = onSettings,
                onUpgrade = onUpgrade,
                onDismissHero = onDismissHero,
                onToolClick = onToolClick,
                onRestoreHero = onRestoreHero,
                onDiscardResults = onDiscardResults,
                isHeroDismissed = isHeroDismissed,
                mainActionModifier = Modifier.guidedTourTarget(DashboardTour.MAIN_ACTION_TARGET),
                settingsModifier = Modifier.guidedTourTarget(DashboardTour.SETTINGS_TARGET),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (items == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val firstToolStableId = remember(items) {
                items.firstOrNull { it is ToolDashboardCardItem }?.stableId
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // While the dashboard tour is active, inflate the grid's bottom contentPadding to
                // (at least) the viewport height. Without that headroom, animateScrollToItem on
                // cards near the end of the list (Swiper, in particular) clamps at max scroll and
                // leaves the target stranded mid-viewport — the bubble then chooses to render
                // above it, squashing the cutout against the screen edge.
                // Clear the bottom dock — which now grows when the hero card is shown — by
                // consuming the Scaffold's measured bottom inset, with a floor for breathing room.
                val dockBottom = maxOf(paddingValues.calculateBottomPadding(), DASHBOARD_BOTTOM_CONTENT_PADDING)
                val bottomContentPadding = if (dashboardTourActive) {
                    maxOf(dockBottom, maxHeight)
                } else {
                    dockBottom
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(390.dp),
                    state = gridState,
                    modifier = Modifier
                        .focusRequester(gridFocusRequester)
                        .focusGroup()
                        // Edge-to-dock jumps: perform the move ourselves; when it fails the
                        // cursor sits at a real grid edge — UP at the top row, LEFT/RIGHT at the
                        // horizontal edges, DOWN below the last row (mid-grid the lazy grid keeps
                        // scrolling or hops columns) — so jump to the dock instead of
                        // dead-ending. Gated on the effective dock visibility so a hidden
                        // (offscreen but still composed) dock can't swallow focus; on TV the dock
                        // is pinned, so the jump is always live.
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val direction = when (event.key) {
                                Key.DirectionUp -> FocusDirection.Up
                                Key.DirectionDown -> FocusDirection.Down
                                Key.DirectionLeft -> FocusDirection.Left
                                Key.DirectionRight -> FocusDirection.Right
                                else -> return@onKeyEvent false
                            }
                            when {
                                focusManager.moveFocus(direction) -> true
                                dockVisible -> {
                                    dockFocusRequester.requestFocus()
                                    true
                                }
                                else -> true
                            }
                        }
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = paddingValues.calculateTopPadding(),
                        bottom = bottomContentPadding,
                    ),
                ) {
                    items(
                        items = items,
                        key = { it.stableId },
                    ) { item ->
                        val tourModifier = when {
                            item is SetupDashboardCardItem ->
                                Modifier.guidedTourTarget(DashboardTour.SETUP_TARGET)
                            item.stableId == firstToolStableId ->
                                Modifier.guidedTourTarget(DashboardTour.TOOLS_TARGET)
                            item is SwiperDashboardCardItem ->
                                Modifier.guidedTourTarget(DashboardTour.MANUAL_TOOL_TARGET)
                            else -> Modifier
                        }
                        // Wrapper Box because DashboardListCard doesn't forward the modifier to
                        // every card type; this guarantees all late-arriving cards animate in.
                        // The per-item requester targets the card's focusable for dock→grid
                        // returns; onFocusEvent keeps the focus memory current (it fires for any
                        // focus gained within the item, including via plain spatial navigation).
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .then(tourModifier)
                                .focusRequester(cardFocusRequesters.getOrPut(item.stableId) { FocusRequester() })
                                .onFocusEvent { if (it.hasFocus) lastFocusedCardId = item.stableId },
                        ) {
                            DashboardListCard(item = item)
                        }
                    }
                }

                if (items.isEmpty() || listState.isEasterEgg) {
                    MascotOverlay()
                }
            }
        }
    }
}

@Composable
private fun MascotOverlay() {
    SdmMascot(modifier = Modifier.fillMaxSize())
}

private val DASHBOARD_BOTTOM_CONTENT_PADDING = 128.dp

/**
 * Scroll the grid so the first item matching [predicate] anchors to the top. Index is resolved
 * here, at invocation time, so item-list refreshes between tour-start and prepareTarget don't
 * leave the tour scrolling to a stale index.
 */
private suspend fun scrollToFirstMatching(
    gridState: LazyGridState,
    items: List<DashboardItem>?,
    predicate: (DashboardItem) -> Boolean,
) {
    val index = items?.indexOfFirst(predicate)?.takeIf { it >= 0 } ?: return
    gridState.animateScrollToItem(index)
}
