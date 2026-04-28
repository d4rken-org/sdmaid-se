package eu.darken.sdmse.main.ui.dashboard

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.R as AppCleanerR
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.debug.recorder.ui.ShortRecordingDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.corpsefinder.R as CorpseFinderR
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionDialog
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionMode
import eu.darken.sdmse.main.ui.navigation.SettingsRoute
import eu.darken.sdmse.main.ui.dashboard.cards.DashboardListCard
import eu.darken.sdmse.main.ui.settings.general.OneClickOptionsDialog
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR
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
        snackbarHostState = snackbarHostState,
        onMainAction = {
            when (val actionState = bottomBarState?.actionState ?: return@DashboardScreen) {
                DashboardViewModel.BottomBarState.Action.DELETE -> {
                    dialogState = DashboardDialogState.MainActionDelete(actionState)
                }

                else -> vm.mainAction(actionState)
            }
        },
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onMainAction: () -> Unit = {},
    onSettings: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onCorpseFinderOneClickChanged: (Boolean) -> Unit = {},
    onSystemCleanerOneClickChanged: (Boolean) -> Unit = {},
    onAppCleanerOneClickChanged: (Boolean) -> Unit = {},
    onDeduplicatorOneClickChanged: (Boolean) -> Unit = {},
) {
    var showOneClickOptions by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(gridState) {
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
                onMainAction = onMainAction,
                onMainActionLongClick = { showOneClickOptions = true },
                onSettings = onSettings,
                onUpgrade = onUpgrade,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val items = listState?.items
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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(390.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = paddingValues.calculateTopPadding(),
                        bottom = 128.dp,
                    ),
                ) {
                    items(
                        items = items,
                        key = { it.stableId },
                    ) { item ->
                        DashboardListCard(item)
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

private val DASHBOARD_BOTTOM_BAR_HEIGHT = 60.dp
private val DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT = 88.dp
private val DASHBOARD_FAB_CORNER_RADIUS = 16.dp
private val DASHBOARD_BOTTOM_BAR_TOP_CORNER_RADIUS = 24.dp
private val DASHBOARD_BOTTOM_BAR_CUTOUT_WIDTH = 66.dp
private val DASHBOARD_BOTTOM_BAR_CUTOUT_DEPTH = 33.dp
private val DASHBOARD_BOTTOM_BAR_CUTOUT_OUTER_RADIUS = 12.dp

private data object DashboardBottomBarShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfWidth = with(density) { (DASHBOARD_BOTTOM_BAR_CUTOUT_WIDTH / 2).toPx() }
        val depth = with(density) { DASHBOARD_BOTTOM_BAR_CUTOUT_DEPTH.toPx() }
        val innerR = with(density) { DASHBOARD_FAB_CORNER_RADIUS.toPx() }
        val outerR = with(density) { DASHBOARD_BOTTOM_BAR_CUTOUT_OUTER_RADIUS.toPx() }
        val topR = with(density) { DASHBOARD_BOTTOM_BAR_TOP_CORNER_RADIUS.toPx() }
        val center = size.width / 2f
        val leftWall = (center - halfWidth).coerceAtLeast(topR + outerR)
        val rightWall = (center + halfWidth).coerceAtMost(size.width - topR - outerR)
        val path = Path().apply {
            moveTo(topR, 0f)
            lineTo(leftWall - outerR, 0f)
            arcTo(
                rect = Rect(leftWall - 2 * outerR, 0f, leftWall, 2 * outerR),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(leftWall, depth - innerR)
            arcTo(
                rect = Rect(leftWall, depth - 2 * innerR, leftWall + 2 * innerR, depth),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(rightWall - innerR, depth)
            arcTo(
                rect = Rect(rightWall - 2 * innerR, depth - 2 * innerR, rightWall, depth),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(rightWall, outerR)
            arcTo(
                rect = Rect(rightWall, 0f, rightWall + 2 * outerR, 2 * outerR),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(size.width - topR, 0f)
            arcTo(
                rect = Rect(size.width - 2 * topR, 0f, size.width, 2 * topR),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(0f, topR)
            arcTo(
                rect = Rect(0f, 0f, 2 * topR, 2 * topR),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun BottomBar(
    state: DashboardViewModel.BottomBarState?,
    isVisible: Boolean,
    onMainAction: () -> Unit,
    onMainActionLongClick: () -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val fabOffsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT,
        animationSpec = if (isVisible) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        },
        label = "dashboardFabOffset",
    )
    val barOffsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT,
        animationSpec = if (isVisible) {
            tween(durationMillis = 300, delayMillis = 150)
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        },
        label = "dashboardBarOffset",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = barOffsetY)
                .padding(top = DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT - DASHBOARD_BOTTOM_BAR_HEIGHT)
                .height(DASHBOARD_BOTTOM_BAR_HEIGHT),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = DashboardBottomBarShape,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = bottomBarSummary(LocalContext.current, state),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp, end = 16.dp),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state?.upgradeInfo?.isPro != true) {
                        IconButton(onClick = onUpgrade) {
                            Icon(
                                imageVector = Icons.Outlined.Stars,
                                contentDescription = stringResource(R.string.upgrades_dashcard_upgrade_action),
                            )
                        }
                    }

                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(CommonR.string.general_settings_title),
                        )
                    }
                }
            }
        }

        state?.takeIf { it.isReady }?.let {
            MainActionFab(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = fabOffsetY),
                actionState = it.actionState,
                onClick = onMainAction,
                onLongClick = onMainActionLongClick,
            )
        }
    }
}

private fun bottomBarSummary(context: android.content.Context, state: DashboardViewModel.BottomBarState?): String {
    if (state == null) return context.getString(easterEggProgressMsg)
    return when {
        state.activeTasks > 0 || state.queuedTasks > 0 -> {
            val activeText = context.resources.getQuantityString(
                R.plurals.tasks_activity_active_notification_message,
                state.activeTasks,
                state.activeTasks,
            )
            val queuedText = context.resources.getQuantityString(
                R.plurals.tasks_activity_queued_notification_message,
                state.queuedTasks,
                state.queuedTasks,
            )
            "$activeText\n$queuedText"
        }

        state.totalItems > 0 || state.totalSize > 0L -> {
            val (formatted, quantity) = ByteFormatter.formatSize(context, state.totalSize)
            val spaceText = context.resources.getQuantityString(
                CommonR.plurals.x_space_can_be_freed,
                quantity,
                formatted,
            )
            val itemsText = context.resources.getQuantityString(
                CommonR.plurals.result_x_items,
                state.totalItems,
                state.totalItems,
            )
            "$spaceText\n$itemsText"
        }

        !state.isReady -> context.getString(easterEggProgressMsg)
        else -> ""
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainActionFab(
    modifier: Modifier = Modifier,
    actionState: DashboardViewModel.BottomBarState.Action,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (containerColor, contentColor) = when (actionState) {
        DashboardViewModel.BottomBarState.Action.SCAN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DashboardViewModel.BottomBarState.Action.DELETE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        DashboardViewModel.BottomBarState.Action.ONECLICK -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        DashboardViewModel.BottomBarState.Action.WORKING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DashboardViewModel.BottomBarState.Action.WORKING_CANCELABLE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = modifier
            .size(56.dp)
            .combinedClickable(
                enabled = actionState != DashboardViewModel.BottomBarState.Action.WORKING,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(DASHBOARD_FAB_CORNER_RADIUS),
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (actionState) {
                DashboardViewModel.BottomBarState.Action.SCAN -> Icon(
                    painter = painterResource(UiR.drawable.ic_layer_search_24),
                    contentDescription = stringResource(CommonR.string.general_scan_action),
                )

                DashboardViewModel.BottomBarState.Action.DELETE -> Icon(
                    painter = painterResource(UiR.drawable.ic_baseline_delete_sweep_24),
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                )

                DashboardViewModel.BottomBarState.Action.ONECLICK -> Icon(
                    painter = painterResource(UiR.drawable.ic_delete_alert_24),
                    contentDescription = stringResource(R.string.dashboard_settings_oneclick_tools_title),
                )

                DashboardViewModel.BottomBarState.Action.WORKING -> Unit

                DashboardViewModel.BottomBarState.Action.WORKING_CANCELABLE -> Icon(
                    painter = painterResource(UiR.drawable.ic_cancel),
                    contentDescription = stringResource(CommonR.string.general_cancel_action),
                )
            }
        }
    }
}

private fun previewBottomBarState(
    action: DashboardViewModel.BottomBarState.Action,
): DashboardViewModel.BottomBarState = DashboardViewModel.BottomBarState(
    isReady = true,
    actionState = action,
    activeTasks = 0,
    queuedTasks = 0,
    totalItems = 37,
    totalSize = 1_024L * 1_024L * 1_024L * 2L,
    upgradeInfo = null,
)

@Preview2
@Composable
private fun DashboardBottomBarPreviewScan() {
    PreviewWrapper {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            BottomBar(
                state = previewBottomBarState(DashboardViewModel.BottomBarState.Action.SCAN),
                isVisible = true,
                onMainAction = {},
                onMainActionLongClick = {},
                onSettings = {},
                onUpgrade = {},
            )
        }
    }
}

@Preview2
@Composable
private fun DashboardBottomBarPreviewDelete() {
    PreviewWrapper {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            BottomBar(
                state = previewBottomBarState(DashboardViewModel.BottomBarState.Action.DELETE),
                isVisible = true,
                onMainAction = {},
                onMainActionLongClick = {},
                onSettings = {},
                onUpgrade = {},
            )
        }
    }
}

internal sealed interface DashboardDialogState {
    data object CorpseFinderDelete : DashboardDialogState
    data object SystemCleanerDelete : DashboardDialogState
    data object AppCleanerDelete : DashboardDialogState
    data class DeduplicatorDelete(
        val clusters: List<Duplicate.Cluster>,
    ) : DashboardDialogState
    data object Todo : DashboardDialogState
    data object ShortRecordingWarning : DashboardDialogState
    data class MainActionDelete(
        val action: DashboardViewModel.BottomBarState.Action,
    ) : DashboardDialogState

    data class UnknownFolders(
        val scannedCount: Int,
        val skippedCount: Int,
        val unknownPaths: List<String>,
    ) : DashboardDialogState
}

@Composable
private fun DashboardEventDialogs(
    state: DashboardDialogState?,
    onDismiss: () -> Unit,
    onConfirmCorpseFinder: () -> Unit,
    onShowCorpseFinder: () -> Unit,
    onConfirmSystemCleaner: () -> Unit,
    onShowSystemCleaner: () -> Unit,
    onConfirmAppCleaner: () -> Unit,
    onShowAppCleaner: () -> Unit,
    onConfirmDeduplicator: () -> Unit,
    onShowDeduplicator: () -> Unit,
    onPreviewDeduplicator: (eu.darken.sdmse.common.previews.PreviewOptions) -> Unit,
    onStopShortRecording: () -> Unit,
    onConfirmMainAction: (DashboardViewModel.BottomBarState.Action) -> Unit,
) {
    when (state) {
        null -> Unit

        DashboardDialogState.CorpseFinderDelete -> DeleteConfirmDialog(
            messageRes = CorpseFinderR.string.corpsefinder_delete_all_confirmation_message,
            onConfirm = { onConfirmCorpseFinder(); onDismiss() },
            onDetails = { onShowCorpseFinder(); onDismiss() },
            onDismiss = onDismiss,
        )

        DashboardDialogState.SystemCleanerDelete -> DeleteConfirmDialog(
            messageRes = SystemCleanerR.string.systemcleaner_delete_all_confirmation_message,
            onConfirm = { onConfirmSystemCleaner(); onDismiss() },
            onDetails = { onShowSystemCleaner(); onDismiss() },
            onDismiss = onDismiss,
        )

        DashboardDialogState.AppCleanerDelete -> DeleteConfirmDialog(
            messageRes = AppCleanerR.string.appcleaner_delete_all_confirmation_message,
            onConfirm = { onConfirmAppCleaner(); onDismiss() },
            onDetails = { onShowAppCleaner(); onDismiss() },
            onDismiss = onDismiss,
        )

        is DashboardDialogState.DeduplicatorDelete -> PreviewDeletionDialog(
            mode = PreviewDeletionMode.All(clusters = state.clusters),
            onConfirm = { onConfirmDeduplicator(); onDismiss() },
            onDismiss = onDismiss,
            onPreviewClick = onPreviewDeduplicator,
            onShowDetails = { onShowDeduplicator(); onDismiss() },
        )

        DashboardDialogState.Todo -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(CommonR.string.general_todo_msg)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )

        DashboardDialogState.ShortRecordingWarning -> ShortRecordingDialog(
            onContinue = {},
            onStopAnyway = onStopShortRecording,
            onDismiss = onDismiss,
        )

        is DashboardDialogState.MainActionDelete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = { Text(stringResource(R.string.dashboard_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmMainAction(state.action)
                    onDismiss()
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )

        is DashboardDialogState.UnknownFolders -> {
            val header = "Scanned ${state.scannedCount} dirs, skipped ${state.skippedCount}"
            val body = if (state.unknownPaths.isEmpty()) {
                "No unknown folders found."
            } else {
                "Found ${state.unknownPaths.size} unknown folder(s):\n\n${state.unknownPaths.joinToString("\n")}"
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Unknown Folders") },
                text = { Text("$header\n\n$body") },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    messageRes: Int,
    onConfirm: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDetails) {
                    Text(stringResource(CommonR.string.general_show_details_action))
                }
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(CommonR.string.general_cancel_action))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(CommonR.string.general_delete_action))
                    }
                }
            }
        },
    )
}
