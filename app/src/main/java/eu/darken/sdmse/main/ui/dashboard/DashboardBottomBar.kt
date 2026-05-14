package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.ui.R as UiR

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
internal fun BottomBar(
    state: DashboardViewModel.BottomBarState?,
    isVisible: Boolean,
    onMainAction: () -> Unit,
    onMainActionLongClick: () -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    mainActionModifier: Modifier = Modifier,
    settingsModifier: Modifier = Modifier,
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
                                imageVector = Icons.TwoTone.Stars,
                                contentDescription = stringResource(R.string.upgrades_dashcard_upgrade_action),
                            )
                        }
                    }

                    IconButton(
                        onClick = onSettings,
                        modifier = settingsModifier,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
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
                    .offset(y = fabOffsetY)
                    .then(mainActionModifier),
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
