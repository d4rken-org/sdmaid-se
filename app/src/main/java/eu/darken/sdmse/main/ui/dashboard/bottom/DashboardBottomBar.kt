package eu.darken.sdmse.main.ui.dashboard.bottom

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SdmInfoChip
import eu.darken.sdmse.common.compose.layout.SdmTooltipAnchor
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.HeroSummary
import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant

// Playful overshoot for the hero's late arrival (ease-out-back).
private val HeroOvershoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * The dashboard bottom dock: a primary-coloured bar, the cradled main-action FAB, and — when the
 * latest main-action produced a result — a floating [DashboardHeroCard] above them.
 *
 * Each piece is bottom-anchored and animates **independently** so they cascade: on show the bar
 * leads, then the FAB, then the hero (with a playful overshoot); on hide they leave in reverse.
 * Each slides fully below the screen (including the nav inset) when hidden.
 */
@Composable
internal fun BottomBar(
    modifier: Modifier = Modifier,
    state: BottomBarState?,
    isVisible: Boolean,
    heroVisible: Boolean,
    onMainAction: () -> Unit,
    onMainActionLongClick: () -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onDismissHero: () -> Unit,
    onToolClick: (HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
    onRestoreHero: () -> Unit = {},
    onDiscardResults: () -> Unit = {},
    isHeroDismissed: Boolean = false,
    focusEscape: FocusRequester? = null,
    mainActionModifier: Modifier = Modifier,
    settingsModifier: Modifier = Modifier,
    upgradeModifier: Modifier = Modifier,
) {
    val heroSummary = state?.heroSummary
    val showHero = heroVisible && heroSummary != null

    // Hidden dock only slides off-screen via offset() — it stays composed for the exit
    // animation, so it must be made unreachable explicitly: gate D-pad/keyboard focus for each
    // layer's subtree on visibility (the hero additionally on showHero, so a dismissed card isn't
    // focusable mid-exit). [focusEscape] routes UP into the dashboard grid — the grid's focus
    // group fully contains the dock's rects and is never a directional candidate on its own.
    val barFocus = Modifier.focusProperties {
        canFocus = isVisible
        focusEscape?.let { up = it }
    }
    val heroFocus = Modifier.focusProperties {
        canFocus = isVisible && showHero
        focusEscape?.let { up = it }
    }

    // Deliberately NOT safeDrawing: that includes the IME inset, and the dock is sized
    // bar-height + navBottom. Composing while an IME inset is (still) reported — e.g. launching
    // with the keyboard up from the launcher — would stretch the bar to keyboard height.
    val navBottom = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val fabBottomInset = DASHBOARD_FAB_BOTTOM_INSET
    val heroBottomInset = DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP

    // Reserved layout height drives the Scaffold's content padding. Elements are bottom-anchored, so
    // growing this only reflows the list above — it never moves the bar/FAB.
    val dockHeight by animateDpAsState(
        targetValue = if (showHero) DASHBOARD_DOCK_HEIGHT_WITH_HERO else DASHBOARD_FAB_SLOT_HEIGHT,
        animationSpec = tween(durationMillis = 300),
        label = "dashboardDockHeight",
    )

    // The bar sits flush with the screen's bottom edge (its surface fills the nav-inset area), so its
    // rest offset is 0; hiding slides it fully below by its whole height (bar + nav inset).
    val barOffsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else DASHBOARD_BAR_HEIGHT + navBottom,
        animationSpec = tween(
            durationMillis = 260,
            delayMillis = if (isVisible) 0 else 150,
            easing = FastOutSlowInEasing,
        ),
        label = "dashboardBarOffset",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (isVisible) -(navBottom + fabBottomInset) else DASHBOARD_FAB_SIZE,
        animationSpec = tween(
            durationMillis = 260,
            delayMillis = 80,
            easing = FastOutSlowInEasing,
        ),
        label = "dashboardFabOffset",
    )
    val heroOffsetY by animateDpAsState(
        targetValue = if (isVisible && showHero) -(navBottom + heroBottomInset) else DASHBOARD_HERO_CARD_HEIGHT,
        animationSpec = tween(
            durationMillis = 340,
            delayMillis = if (isVisible && showHero) 150 else 0,
            easing = if (isVisible && showHero) HeroOvershoot else FastOutSlowInEasing,
        ),
        label = "dashboardHeroOffset",
    )
    val heroAlpha by animateFloatAsState(
        targetValue = if (isVisible && showHero) 1f else 0f,
        animationSpec = tween(durationMillis = 200, delayMillis = if (isVisible && showHero) 150 else 0),
        label = "dashboardHeroAlpha",
    )

    // Swipe-down-to-dismiss for the hero. BottomBar owns the drag so it composes with the existing
    // heroOffsetY/heroAlpha exit: past the threshold we just call onDismissHero() and let that exit
    // finish from the dragged position; a short drag springs back. Reset whenever the hero
    // (re)appears so a restored card never starts displaced.
    val density = LocalDensity.current
    val heroDismissDistancePx = with(density) { DASHBOARD_HERO_CARD_HEIGHT.toPx() }
    val heroDismissThresholdPx = heroDismissDistancePx * 0.35f
    val heroFlingVelocityCutoff = with(density) { 500.dp.toPx() }
    var heroDragPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(showHero) { if (showHero) heroDragPx = 0f }
    val heroDragState = rememberDraggableState { delta ->
        heroDragPx = (heroDragPx + delta).coerceAtLeast(0f)
    }

    // The FAB only exists once the dashboard is ready; the bar drops its FAB cutout until then so it
    // never shows an empty notch (e.g. during the initial load). Same flag that gates the FAB below.
    val fabPresent = state?.isReady == true
    val barShape = dashboardBarShape(isReady = fabPresent)

    // While hidden, the bar/FAB remain composed below the screen — drop them from the
    // accessibility tree too, so TalkBack can't reach off-screen controls focus gating misses.
    val a11yGate = if (isVisible) Modifier else Modifier.clearAndSetSemantics { }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight + navBottom)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .then(a11yGate),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = barOffsetY)
                .height(DASHBOARD_BAR_HEIGHT + navBottom)
                .then(barFocus),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = barShape,
            shadowElevation = 8.dp,
        ) {
            BarContent(
                state = state,
                compactSummary = heroSummary?.takeIf { !showHero },
                onSettings = onSettings,
                onUpgrade = onUpgrade,
                // Only the user-dismissed compact chip restores the hero; during a tour the same
                // chip stays a passive info chip (the tour suppresses the floating hero on purpose).
                onRestoreHero = onRestoreHero.takeIf { isHeroDismissed },
                contentBottomPadding = navBottom,
                settingsModifier = settingsModifier,
                upgradeModifier = upgradeModifier,
            )
        }

        // Composed while shown or mid-exit; dropped once fully hidden so it leaves the semantics
        // tree (no invisible node for TalkBack to read) and the exit animation can still play out.
        if (heroSummary != null && (showHero || heroAlpha > 0f)) {
            DashboardHeroCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(heroFocus)
                    .offset(y = heroOffsetY)
                    .graphicsLayer {
                        translationY = heroDragPx
                        alpha = heroAlpha * (1f - heroDragPx / heroDismissDistancePx).coerceIn(0f, 1f)
                    }
                    .fillMaxWidth()
                    .height(DASHBOARD_HERO_CARD_HEIGHT)
                    .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN)
                    .draggable(
                        state = heroDragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (heroDragPx > heroDismissThresholdPx || velocity > heroFlingVelocityCutoff) {
                                onDismissHero()
                            } else {
                                animate(heroDragPx, 0f, animationSpec = spring()) { value, _ -> heroDragPx = value }
                            }
                        },
                    ),
                summary = heroSummary,
                now = state?.now ?: Instant.EPOCH,
                onDismiss = onDismissHero,
                // Discarding only makes sense while there's still pending data; a FREED summary is
                // already just an after-the-fact report the X can hide.
                onDiscard = onDiscardResults
                    .takeIf { heroSummary.mode == HeroSummary.Mode.FREEABLE },
                onToolClick = onToolClick,
            )
        }

        state?.takeIf { it.isReady }?.let {
            MainActionFab(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = fabOffsetY)
                    .then(barFocus)
                    .then(mainActionModifier),
                actionState = it.actionState,
                onClick = onMainAction,
                onLongClick = onMainActionLongClick,
            )
        }
    }
}

@Composable
private fun BarContent(
    modifier: Modifier = Modifier,
    state: BottomBarState?,
    compactSummary: HeroSummary?,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onRestoreHero: (() -> Unit)? = null,
    contentBottomPadding: Dp = 0.dp,
    settingsModifier: Modifier = Modifier,
    upgradeModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The surface extends behind the nav inset; keep the controls in the visible bar band by
    // padding the bottom up by that inset.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 8.dp + contentBottomPadding),
    ) {
        when {
            state != null && (state.activeTasks > 0 || state.queuedTasks > 0) -> {
                val active = pluralStringResource(
                    R.plurals.tasks_activity_active_notification_message,
                    state.activeTasks,
                    state.activeTasks,
                )
                val queued = pluralStringResource(
                    R.plurals.tasks_activity_queued_notification_message,
                    state.queuedTasks,
                    state.queuedTasks,
                )
                Text(
                    text = "$active\n$queued",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 16.dp),
                )
            }

            compactSummary != null -> {
                SdmInfoChip(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    icon = painterResource(UiR.drawable.ic_baseline_delete_sweep_24),
                    label = ByteFormatter.formatSize(context, compactSummary.totalSize).first,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    // Tapping it re-shows a dismissed hero (null during a tour → passive chip).
                    onClick = onRestoreHero,
                )
            }

            state == null || !state.isReady -> {
                Text(
                    text = stringResource(easterEggProgressMsg),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 16.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state?.upgradeInfo?.isPro != true) {
                SdmTooltipIconButton(
                    icon = Icons.TwoTone.Stars,
                    label = stringResource(R.string.upgrades_dashcard_upgrade_action),
                    onClick = onUpgrade,
                    modifier = upgradeModifier,
                    anchor = SdmTooltipAnchor.ABOVE,
                )
            }

            SdmTooltipIconButton(
                icon = Icons.TwoTone.Settings,
                label = stringResource(CommonR.string.general_settings_title),
                onClick = onSettings,
                modifier = settingsModifier,
                anchor = SdmTooltipAnchor.ABOVE,
            )
        }
    }
}

@Composable
private fun MainActionFab(
    modifier: Modifier = Modifier,
    actionState: BottomBarState.Action,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (containerColor, contentColor) = when (actionState) {
        BottomBarState.Action.SCAN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BottomBarState.Action.DELETE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        BottomBarState.Action.ONECLICK -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        BottomBarState.Action.WORKING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BottomBarState.Action.WORKING_CANCELABLE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = modifier.size(DASHBOARD_FAB_SIZE),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(DASHBOARD_FAB_CORNER_RADIUS),
        // Kept low so the FAB's downward shadow doesn't darken the lower cradle and skew the
        // visual top/bottom symmetry of the notch.
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    enabled = actionState != BottomBarState.Action.WORKING,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (actionState) {
                BottomBarState.Action.SCAN -> Icon(
                    painter = painterResource(UiR.drawable.ic_layer_search_24),
                    contentDescription = stringResource(CommonR.string.general_scan_action),
                )

                BottomBarState.Action.DELETE -> Icon(
                    painter = painterResource(UiR.drawable.ic_baseline_delete_sweep_24),
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                )

                BottomBarState.Action.ONECLICK -> Icon(
                    painter = painterResource(UiR.drawable.ic_delete_alert_24),
                    contentDescription = stringResource(R.string.dashboard_settings_oneclick_tools_title),
                )

                BottomBarState.Action.WORKING -> Unit

                BottomBarState.Action.WORKING_CANCELABLE -> Icon(
                    painter = painterResource(UiR.drawable.ic_cancel),
                    contentDescription = stringResource(CommonR.string.general_cancel_action),
                )
            }
        }
    }
}

private fun previewBottomBarState(
    action: BottomBarState.Action,
    heroSummary: HeroSummary? = null,
): BottomBarState = BottomBarState(
    isReady = true,
    actionState = action,
    activeTasks = 0,
    queuedTasks = 0,
    heroSummary = heroSummary,
    upgradeInfo = null,
)

private fun previewHeroSummary() = HeroSummary(
    mode = HeroSummary.Mode.FREEABLE,
    totalSize = 1_024L * 1_024L * 1_024L * 2L,
    itemCount = 37,
    tools = listOf(
        HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
        HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 1_024L, 25),
    ),
)

@Preview2
@Composable
private fun DashboardBottomBarPreviewHero() {
    PreviewWrapper {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) {
            BottomBar(
                state = previewBottomBarState(BottomBarState.Action.DELETE, previewHeroSummary()),
                isVisible = true,
                heroVisible = true,
                onMainAction = {},
                onMainActionLongClick = {},
                onSettings = {},
                onUpgrade = {},
                onDismissHero = {},
            )
        }
    }
}

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
                state = previewBottomBarState(BottomBarState.Action.SCAN),
                isVisible = true,
                heroVisible = false,
                onMainAction = {},
                onMainActionLongClick = {},
                onSettings = {},
                onUpgrade = {},
                onDismissHero = {},
            )
        }
    }
}
