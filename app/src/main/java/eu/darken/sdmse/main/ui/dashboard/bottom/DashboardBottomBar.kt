package eu.darken.sdmse.main.ui.dashboard.bottom

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
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
import eu.darken.sdmse.main.ui.dashboard.showsUpgradeBlock
import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant

// Playful overshoot for the hero's and FAB's late arrival (ease-out-back).
private val BubbleOvershoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * The dashboard bottom dock: a primary-coloured bar, the cradled main-action FAB, and — when the
 * latest main-action produced a result — a floating [DashboardHeroCard] above them.
 *
 * Each piece is bottom-anchored and animates **independently** so they cascade: on show the bar
 * leads, the FAB grows out of its cradle, then the hero arrives with a playful overshoot; on hide
 * they leave in reverse. The bar and hero slide below the screen, while the FAB shrinks and fades
 * in place so it never travels through the bar.
 */
@Composable
internal fun BottomBar(
    modifier: Modifier = Modifier,
    state: BottomBarState?,
    isVisible: Boolean,
    heroVisible: Boolean,
    onMainAction: () -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onDismissHero: () -> Unit,
    onToolClick: (HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
    onLockedToolClick: (SDMTool.Type) -> Unit = {},
    onExpandHero: () -> Unit = {},
    onDiscardResults: () -> Unit = {},
    canExpandHero: Boolean = false,
    mainActionModifier: Modifier = Modifier,
    settingsModifier: Modifier = Modifier,
    upgradeModifier: Modifier = Modifier,
) {
    val heroSummary = state?.heroSummary
    val showHero = heroVisible && heroSummary != null

    // The hidden dock stays composed for its exit animation, so it must be made unreachable
    // explicitly: gate D-pad/keyboard focus for each layer's subtree on visibility (the hero
    // additionally on showHero, so a dismissed card isn't focusable mid-exit). Grid↔dock D-pad
    // crossings are handled by the caller's key handlers on the dock's focus group (see
    // DashboardScreen); the only bridge here is the UP rung from the bar/FAB into the hero's entry
    // control (Discard, or the Dismiss X), which would otherwise be unreachable — spatial search
    // skips the hero's top-right controls.
    val heroEntryFocusRequester = remember { FocusRequester() }
    val barFocus = Modifier.focusProperties {
        canFocus = isVisible
        if (showHero) up = heroEntryFocusRequester
    }
    val heroFocus = Modifier.focusProperties {
        canFocus = isVisible && showHero
    }

    // Deliberately NOT safeDrawing: that includes the IME inset, and the dock is sized
    // bar-height + navBottom. Composing while an IME inset is (still) reported — e.g. launching
    // with the keyboard up from the launcher — would stretch the bar to keyboard height.
    val navBottom = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val fabBottomInset = DASHBOARD_FAB_BOTTOM_INSET
    val heroBottomInset = DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP

    // Hero card + dock reservation grow with the font scale so large text doesn't clip the card's
    // caption/hint. One read each, reused below for the card height, the dock reservation, the
    // hidden-offset slide distance, and the swipe-to-dismiss threshold — they must stay in lockstep.
    // Read from the same predicate the card renders by, or the reservation and the layout disagree
    // and the card either clips or leaves dead space behind it.
    val heroShowsUpgradeBlock = heroSummary?.showsUpgradeBlock == true
    val heroCardHeight = dashboardHeroCardHeight(heroShowsUpgradeBlock)
    val dockHeightWithHero = dashboardDockHeightWithHero(heroShowsUpgradeBlock)

    // Reserved layout height drives the Scaffold's content padding. Elements are bottom-anchored, so
    // growing this only reflows the list above — it never moves the bar/FAB.
    val dockHeight by animateDpAsState(
        targetValue = if (showHero) dockHeightWithHero else DASHBOARD_FAB_SLOT_HEIGHT,
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
    // Keep the FAB fixed over its cradle. It bubbles in after the bar starts arriving and shrinks
    // completely before the bar begins its delayed exit, avoiding the old trip through the dock.
    // The node is the FAB's touch box, which is DASHBOARD_FAB_TOUCH_SLACK taller than the visual on
    // each side — drop it by that slack so the *visible* FAB still rests at navBottom + inset.
    val fabOffsetY = -(navBottom + fabBottomInset - DASHBOARD_FAB_TOUCH_SLACK)
    val fabScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 240 else 150,
            delayMillis = if (isVisible) 80 else 0,
            easing = if (isVisible) BubbleOvershoot else FastOutSlowInEasing,
        ),
        label = "dashboardFabScale",
    )
    val fabAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 140 else 100,
            delayMillis = if (isVisible) 80 else 0,
        ),
        label = "dashboardFabAlpha",
    )
    val heroOffsetY by animateDpAsState(
        targetValue = if (isVisible && showHero) -(navBottom + heroBottomInset) else heroCardHeight,
        animationSpec = tween(
            durationMillis = 340,
            delayMillis = if (isVisible && showHero) 150 else 0,
            easing = if (isVisible && showHero) BubbleOvershoot else FastOutSlowInEasing,
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
    val heroDismissDistancePx = with(density) { heroCardHeight.toPx() }
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

    // While hidden, the dock remains composed for its exit animations — drop it from the
    // accessibility tree too, so TalkBack can't reach controls that focus gating misses.
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
                // The chip expands the hero whenever one is available and collapsed — never
                // auto-shown (card-triggered) or dismissed by the user. During a tour it stays a
                // passive info chip (the tour suppresses the floating hero on purpose).
                onExpandHero = onExpandHero.takeIf { canExpandHero },
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
                    .height(heroCardHeight)
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
                onLockedToolClick = onLockedToolClick,
                onUpgrade = onUpgrade,
                entryFocusRequester = heroEntryFocusRequester,
            )
        }

        state?.takeIf { it.isReady }?.let {
            MainActionFab(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = fabOffsetY)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                        alpha = fabAlpha
                    }
                    .then(barFocus)
                    .then(mainActionModifier),
                actionState = it.actionState,
                enabled = isVisible,
                onClick = onMainAction,
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
    onExpandHero: (() -> Unit)? = null,
    contentBottomPadding: Dp = 0.dp,
    settingsModifier: Modifier = Modifier,
    upgradeModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The surface extends behind the nav inset; keep the controls in the visible bar band by
    // padding the bottom up by that inset.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 8.dp + contentBottomPadding),
    ) {
        // Everything left of the cradled FAB, measured off the FAB's actual touch box rather than a
        // fixed fraction of the bar: the text below is long in several locales, and the fraction was
        // leaving usable dp unused right next to it.
        val infoSlot = Modifier
            .align(Alignment.CenterStart)
            .padding(start = BAR_INFO_START_PADDING)
            .width(
                ((maxWidth - DASHBOARD_FAB_TOUCH_SIZE) / 2 - BAR_INFO_START_PADDING - BAR_INFO_FAB_GAP)
                    .coerceAtLeast(0.dp)
            )
        // Half the visible bar band, floored to a whole pixel: a line box is rounded *up* to the
        // pixel grid, so splitting an odd band evenly would round the pair one pixel past the band
        // and clip the second counter again.
        val infoLineSlot = with(LocalDensity.current) { floor(maxHeight.toPx() / 2f).toDp() }

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
                // One TalkBack stop, as when this was a single two-line Text — the split is a
                // layout concern and shouldn't cost the user an extra swipe.
                Column(modifier = infoSlot.semantics(mergeDescendants = true) { }) {
                    BarInfoLine(text = active, lineSlot = infoLineSlot)
                    BarInfoLine(text = queued, lineSlot = infoLineSlot)
                }
            }

            compactSummary != null -> {
                // A locked-only summary carries its amount in the locked slices — reading totalSize
                // here would advertise "0 B". The star marks it as gated, and it has to be spelled
                // out for TalkBack: the chip's icon is decorative unless described, so otherwise the
                // announcement is a bare size with no hint that the space is out of reach.
                val isLocked = compactSummary.mode == HeroSummary.Mode.LOCKED_ONLY
                SdmInfoChip(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    icon = if (isLocked) {
                        rememberVectorPainter(Icons.TwoTone.Stars)
                    } else {
                        painterResource(UiR.drawable.ic_baseline_delete_sweep_24)
                    },
                    label = ByteFormatter.formatSize(
                        context,
                        if (isLocked) compactSummary.lockedSize else compactSummary.totalSize,
                    ).first,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconContentDescription = stringResource(R.string.dashboard_hero_locked_chip_description)
                        .takeIf { isLocked },
                    // Tapping it expands the collapsed hero (null during a tour → passive chip).
                    onClick = onExpandHero,
                )
            }

            state == null || !state.isReady -> {
                Text(
                    text = stringResource(easterEggProgressMsg),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    // Clip would drop an overlong message's tail with nothing to show for it.
                    overflow = TextOverflow.Ellipsis,
                    modifier = infoSlot,
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

/** Inset of the bar's info slot from the start edge. */
private val BAR_INFO_START_PADDING = 16.dp

/** Breathing room between the info slot and the cradled FAB's touch box. */
private val BAR_INFO_FAB_GAP = 8.dp

/** Floor for [BarInfoLine]'s auto-sizing. Below this the counts stop being readable at a glance. */
private val BAR_INFO_MIN_FONT_SIZE = 9.sp

/** Used only if the theme's `bodySmall` ever loses its explicit size/line height. */
private val BAR_INFO_FALLBACK_FONT_SIZE = 12.sp
private const val BAR_INFO_FALLBACK_LINE_RATIO = 4f / 3f

/**
 * One line of the bar's info slot, sized to fit [lineSlot] tall and its slot wide.
 *
 * Each count gets its own single-line [Text]. Rendering both as one `"$a\n$b"` string capped at two
 * lines meant a wrap in the first line consumed the second line's budget and the queued count
 * vanished outright (issue #2698).
 *
 * The line shrinks rather than wraps, and only ellipsizes in the *middle* once it hits
 * [BAR_INFO_MIN_FONT_SIZE]: several locales put the number last ("Задач в очереди: %d"), so a tail
 * truncation would cut exactly the count the line exists to show.
 *
 * Auto-size alone can't keep the pair inside the bar: with an ellipsizing overflow its fit test is
 * "was this line ellipsized", which is a width question, and the bar band is a fixed dp height that
 * a 200% font scale overruns. Hence the explicit ceiling — the largest font whose line box still
 * fits [lineSlot].
 */
@Composable
private fun BarInfoLine(
    text: String,
    lineSlot: Dp,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodySmall
    val baseSize = if (style.fontSize.isSp) style.fontSize else BAR_INFO_FALLBACK_FONT_SIZE
    val density = LocalDensity.current

    // All of this is computed in dp, not sp. Android's font scaling is non-linear above 100%, so
    // an sp ratio is not the rendered ratio (at 200%, 12sp and 16sp do not render 3:4) and doing the
    // arithmetic in sp shrinks the text further than it has to.
    val baseSizeDp = with(density) { baseSize.toDp() }
    val baseLineDp = when {
        style.lineHeight.isSp -> with(density) { style.lineHeight.toDp() }
        else -> baseSizeDp * BAR_INFO_FALLBACK_LINE_RATIO
    }
    val lineRatio = if (baseSizeDp.value > 0f) baseLineDp / baseSizeDp else BAR_INFO_FALLBACK_LINE_RATIO

    // The style's line height is absolute, so shrinking the font on its own leaves the line *box* as
    // tall as ever and the second counter keeps hanging out of the bar. Cap the box at the slot
    // (never above the theme's own value, so ordinary text scales look untouched), then cap the font
    // at what fits that box.
    val lineCapDp = minOf(baseLineDp, lineSlot)
    val ceiling = with(density) { (lineCapDp / lineRatio).toSp() }.value.coerceIn(1f, baseSize.value)

    Text(
        modifier = modifier,
        text = text,
        style = style.copy(lineHeight = with(density) { lineCapDp.toSp() }),
        autoSize = TextAutoSize.StepBased(
            minFontSize = BAR_INFO_MIN_FONT_SIZE.value.coerceAtMost(ceiling).sp,
            maxFontSize = ceiling.sp,
            stepSize = 0.5.sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

/**
 * The cradled main action.
 *
 * The clickable is the outer [DASHBOARD_FAB_TOUCH_SIZE] box, not the visible [DASHBOARD_FAB_SIZE]
 * surface, so the notch clearance around the FAB is live instead of falling through to the grid.
 * The ripple can't ride along on that clickable (it would draw an oversized square) nor move onto
 * the surface (the press hotspot would land [DASHBOARD_FAB_TOUCH_SLACK] off), so it gets its own
 * same-sized overlay clipped to [DashboardFabRippleShape].
 *
 * No long-press: the primary action must fire on any press length. The one-click tool options this
 * used to open live in Settings > General.
 */
@Composable
private fun MainActionFab(
    modifier: Modifier = Modifier,
    actionState: BottomBarState.Action,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (containerColor, contentColor) = when (actionState) {
        BottomBarState.Action.SCAN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BottomBarState.Action.DELETE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        BottomBarState.Action.ONECLICK -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        BottomBarState.Action.WORKING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BottomBarState.Action.WORKING_CANCELABLE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(DASHBOARD_FAB_TOUCH_SIZE)
            .clickable(
                enabled = enabled && actionState != BottomBarState.Action.WORKING,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(DASHBOARD_FAB_SIZE),
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(DASHBOARD_FAB_CORNER_RADIUS),
            // Kept low so the FAB's downward shadow doesn't darken the lower cradle and skew the
            // visual top/bottom symmetry of the notch.
            shadowElevation = 4.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
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

                    BottomBarState.Action.WORKING -> {
                        val workingLabel = stringResource(R.string.widget_home_working)
                        Box(Modifier.semantics { contentDescription = workingLabel })
                    }

                    BottomBarState.Action.WORKING_CANCELABLE -> Icon(
                        painter = painterResource(UiR.drawable.ic_cancel),
                        contentDescription = stringResource(CommonR.string.general_cancel_action),
                    )
                }
            }
        }

        // Drawn by a node the same size and origin as the clickable, so the press hotspot lands
        // under the finger even out in the slack, but clipped to the FAB's own outline so it never
        // bleeds into the cradle. Colour is passed explicitly — out here LocalContentColor is the
        // bar's, not the FAB's.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(DashboardFabRippleShape)
                .indication(interactionSource, ripple(color = contentColor)),
        )
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
                onSettings = {},
                onUpgrade = {},
                onDismissHero = {},
            )
        }
    }
}
