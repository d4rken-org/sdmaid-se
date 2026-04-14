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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.R as AppCleanerR
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.MascotView
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.debug.recorder.ui.ShortRecordingDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.corpsefinder.R as CorpseFinderR
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.main.ui.navigation.SettingsRoute
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

    val listState by vm.listState.collectAsStateWithLifecycle(initialValue = null)
    val bottomBarState by vm.bottomBarState.collectAsStateWithLifecycle(initialValue = null)
    val oneClickOptionsState by vm.oneClickOptionsState.collectAsStateWithLifecycle(
        initialValue = DashboardViewModel.OneClickOptionsState(),
    )

    LaunchedEffect(vm, activity, snackbarHostState) {
        vm.events.collect { event ->
            when (event) {
                is DashboardEvents.CorpseFinderDeleteConfirmation -> {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(CommonR.string.general_delete_confirmation_title)
                        .setMessage(CorpseFinderR.string.corpsefinder_delete_all_confirmation_message)
                        .setPositiveButton(CommonR.string.general_delete_action) { _, _ -> vm.confirmCorpseDeletion() }
                        .setNegativeButton(CommonR.string.general_cancel_action, null)
                        .setNeutralButton(CommonR.string.general_show_details_action) { _, _ -> vm.showCorpseFinder() }
                        .show()
                }

                is DashboardEvents.SystemCleanerDeleteConfirmation -> {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(CommonR.string.general_delete_confirmation_title)
                        .setMessage(SystemCleanerR.string.systemcleaner_delete_all_confirmation_message)
                        .setPositiveButton(CommonR.string.general_delete_action) { _, _ -> vm.confirmFilterContentDeletion() }
                        .setNegativeButton(CommonR.string.general_cancel_action, null)
                        .setNeutralButton(CommonR.string.general_show_details_action) { _, _ -> vm.showSystemCleaner() }
                        .show()
                }

                is DashboardEvents.AppCleanerDeleteConfirmation -> {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(CommonR.string.general_delete_confirmation_title)
                        .setMessage(AppCleanerR.string.appcleaner_delete_all_confirmation_message)
                        .setPositiveButton(CommonR.string.general_delete_action) { _, _ -> vm.confirmAppJunkDeletion() }
                        .setNegativeButton(CommonR.string.general_cancel_action, null)
                        .setNeutralButton(CommonR.string.general_show_details_action) { _, _ -> vm.showAppCleaner() }
                        .show()
                }

                is DashboardEvents.DeduplicatorDeleteConfirmation -> {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(CommonR.string.general_delete_confirmation_title)
                        .setMessage(DeduplicatorR.string.deduplicator_delete_confirmation_message)
                        .setPositiveButton(CommonR.string.general_delete_action) { _, _ -> vm.confirmDeduplicatorDeletion() }
                        .setNegativeButton(CommonR.string.general_cancel_action, null)
                        .setNeutralButton(CommonR.string.general_show_details_action) { _, _ -> vm.showDeduplicator() }
                        .show()
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
                    MaterialAlertDialogBuilder(activity)
                        .setMessage(CommonR.string.general_todo_msg)
                        .show()
                }

                is DashboardEvents.OpenIntent -> {
                    try {
                        activity.startActivity(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        e.asErrorDialogBuilder(activity).show()
                    }
                }

                DashboardEvents.SqueezerSetup -> vm.navTo(SqueezerSetupRoute)

                DashboardEvents.ShowShortRecordingWarning -> {
                    ShortRecordingDialog(
                        context = activity,
                        onContinue = {},
                        onStopAnyway = vm::confirmStopRecording,
                    ).show()
                }

                is DashboardEvents.ShowUnknownFolders -> {
                    val header = "Scanned ${event.scannedCount} dirs, skipped ${event.skippedCount}"
                    val body = if (event.unknownPaths.isEmpty()) {
                        "No unknown folders found."
                    } else {
                        "Found ${event.unknownPaths.size} unknown folder(s):\n\n${event.unknownPaths.joinToString("\n")}"
                    }
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Unknown Folders")
                        .setMessage("$header\n\n$body")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    DashboardScreen(
        listState = listState,
        bottomBarState = bottomBarState,
        oneClickOptionsState = oneClickOptionsState,
        snackbarHostState = snackbarHostState,
        onMainAction = {
            when (val actionState = bottomBarState?.actionState ?: return@DashboardScreen) {
                DashboardViewModel.BottomBarState.Action.DELETE -> {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(CommonR.string.general_delete_confirmation_title)
                        .setMessage(R.string.dashboard_delete_all_message)
                        .setPositiveButton(CommonR.string.general_delete_action) { _, _ -> vm.mainAction(actionState) }
                        .setNegativeButton(CommonR.string.general_cancel_action, null)
                        .show()
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
            state = oneClickOptionsState,
            onDismiss = { showOneClickOptions = false },
            onCorpseFinderChanged = onCorpseFinderOneClickChanged,
            onSystemCleanerChanged = onSystemCleanerOneClickChanged,
            onAppCleanerChanged = onAppCleanerOneClickChanged,
            onDeduplicatorChanged = onDeduplicatorOneClickChanged,
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
    AndroidView(
        factory = { context -> MascotView(context) },
        modifier = Modifier.fillMaxSize(),
    )
}

private val DASHBOARD_BOTTOM_BAR_HEIGHT = 60.dp
private val DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT = 88.dp
private val DASHBOARD_BOTTOM_BAR_CUTOUT_WIDTH = 88.dp
private val DASHBOARD_BOTTOM_BAR_CUTOUT_DEPTH = 34.dp
private val DASHBOARD_BOTTOM_BAR_CUTOUT_CORNER_RADIUS = 32.dp

private data object DashboardBottomBarShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cutoutHalfWidth = with(density) { (DASHBOARD_BOTTOM_BAR_CUTOUT_WIDTH / 2).toPx() }
        val cutoutDepth = with(density) { DASHBOARD_BOTTOM_BAR_CUTOUT_DEPTH.toPx() }
        val cornerRadius = with(density) { DASHBOARD_BOTTOM_BAR_CUTOUT_CORNER_RADIUS.toPx() }
        val center = size.width / 2f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo((center - cutoutHalfWidth - cornerRadius).coerceAtLeast(0f), 0f)
            cubicTo(
                center - cutoutHalfWidth, 0f,
                center - cutoutHalfWidth, cutoutDepth,
                center, cutoutDepth,
            )
            cubicTo(
                center + cutoutHalfWidth, cutoutDepth,
                center + cutoutHalfWidth, 0f,
                (center + cutoutHalfWidth + cornerRadius).coerceAtMost(size.width), 0f,
            )
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
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
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT,
        label = "dashboardBottomBarOffset",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .height(DASHBOARD_BOTTOM_BAR_SLOT_HEIGHT),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
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
                                painter = painterResource(UiR.drawable.ic_baseline_stars_24),
                                contentDescription = stringResource(R.string.upgrades_dashcard_upgrade_action),
                            )
                        }
                    }

                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(CommonR.string.general_settings_title),
                        )
                    }
                }
            }
        }

        state?.takeIf { it.isReady }?.let {
            MainActionFab(
                modifier = Modifier.align(Alignment.TopCenter),
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
        shape = RoundedCornerShape(16.dp),
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

@Composable
private fun OneClickOptionsDialog(
    state: DashboardViewModel.OneClickOptionsState,
    onDismiss: () -> Unit,
    onCorpseFinderChanged: (Boolean) -> Unit,
    onSystemCleanerChanged: (Boolean) -> Unit,
    onAppCleanerChanged: (Boolean) -> Unit,
    onDeduplicatorChanged: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dashboard_settings_oneclick_tools_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_settings_oneclick_tools_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SwitchRow(
                    label = stringResource(CommonR.string.corpsefinder_tool_name),
                    checked = state.corpseFinderEnabled,
                    onCheckedChange = onCorpseFinderChanged,
                )
                SwitchRow(
                    label = stringResource(CommonR.string.systemcleaner_tool_name),
                    checked = state.systemCleanerEnabled,
                    onCheckedChange = onSystemCleanerChanged,
                )
                SwitchRow(
                    label = stringResource(CommonR.string.appcleaner_tool_name),
                    checked = state.appCleanerEnabled,
                    onCheckedChange = onAppCleanerChanged,
                )
                SwitchRow(
                    label = stringResource(CommonR.string.deduplicator_tool_name),
                    checked = state.deduplicatorEnabled,
                    onCheckedChange = onDeduplicatorChanged,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
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
