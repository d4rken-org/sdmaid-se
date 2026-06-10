package eu.darken.sdmse.main.ui.dashboard

import android.app.Activity
import android.content.ActivityNotFoundException
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
import androidx.compose.ui.platform.LocalContext
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
        oneClickOptionsState = oneClickOptionsState,
        isHeroDismissed = isHeroDismissed,
        snackbarHostState = snackbarHostState,
        onDismissHero = vm::dismissHero,
        onMainAction = {
            when (val actionState = bottomBarState?.actionState ?: return@DashboardScreen) {
                DashboardViewModel.BottomBarState.Action.DELETE -> {
                    dialogState = DashboardDialogState.MainActionDelete(actionState)
                }

                else -> vm.mainAction(actionState)
            }
        },
        onToolClick = vm::onHeroToolClick,
        onRestoreHero = vm::restoreHero,
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
    bottomBarState: DashboardViewModel.BottomBarState? = null,
    oneClickOptionsState: DashboardViewModel.OneClickOptionsState = DashboardViewModel.OneClickOptionsState(),
    isHeroDismissed: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onMainAction: () -> Unit = {},
    onToolClick: (DashboardViewModel.HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
    onRestoreHero: () -> Unit = {},
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
    // Tour-aware screens watch this and freeze any transient chrome (auto-hiding bottom bar,
    // dismiss-on-scroll banners, etc.) so tour targets stay visible when prepareTarget
    // scrolls or when steps cycle through.
    val tourSession by tourController.session.collectAsStateWithLifecycle()
    // Narrow to *this* screen's tour. A global tour spawned from elsewhere shouldn't be allowed
    // to inflate dashboard bottom padding or otherwise change layout here.
    val dashboardTourActive = tourSession?.definition?.id == DashboardTour.id

    // Auto-hide the bottom bar on scroll, BUT freeze it visible whenever a tour is active.
    // Re-keying on dashboardTourActive cancels the scroll listener for the duration of the tour.
    LaunchedEffect(gridState, dashboardTourActive) {
        if (dashboardTourActive) {
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

    Scaffold(
        bottomBar = {
            BottomBar(
                state = bottomBarState,
                isVisible = isBottomBarVisible,
                // Suppress the hero while a tour is active so it can't cover or fight tour targets.
                heroVisible = bottomBarState?.heroSummary != null && !isHeroDismissed && !dashboardTourActive,
                onMainAction = onMainAction,
                onMainActionLongClick = { showOneClickOptions = true },
                onSettings = onSettings,
                onUpgrade = onUpgrade,
                onDismissHero = onDismissHero,
                onToolClick = onToolClick,
                onRestoreHero = onRestoreHero,
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
                // Clear the bottom chrome — which now grows when the hero card is shown — by
                // consuming the Scaffold's measured bottom inset, with a floor for breathing room.
                val chromeBottom = maxOf(paddingValues.calculateBottomPadding(), DASHBOARD_BOTTOM_CONTENT_PADDING)
                val bottomContentPadding = if (dashboardTourActive) {
                    maxOf(chromeBottom, maxHeight)
                } else {
                    chromeBottom
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(390.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
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
                        Box(modifier = Modifier.animateItem().then(tourModifier)) {
                            DashboardListCard(item)
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
