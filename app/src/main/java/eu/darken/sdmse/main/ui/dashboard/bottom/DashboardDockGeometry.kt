package eu.darken.sdmse.main.ui.dashboard.bottom

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Shared geometry for the dashboard bottom dock (bar + cradled FAB + hero card).
// The hero card's bottom notch is the *exact same* cutout as the bar's top notch, mirrored, so the
// FAB nestles identically into both with a uniform gap.

internal val DASHBOARD_BAR_HEIGHT = 60.dp

internal val DASHBOARD_FAB_SIZE = 56.dp
internal val DASHBOARD_FAB_CORNER_RADIUS = 16.dp

/**
 * Distance from the screen's bottom edge to the FAB's bottom. Chosen so the FAB's centre sits on the
 * mid-line of the bar↔hero gap, making it dip into the bar notch and poke into the hero notch by the
 * same amount (a symmetric cradle): BAR_HEIGHT + GAP/2 - FAB_SIZE/2 = 60 + 6 - 28 = 38.
 */
internal val DASHBOARD_FAB_BOTTOM_INSET = 38.dp

/** Reserved dock height when no hero is shown; must clear the FAB's top (38 + 56 = 94) + breathing room. */
internal val DASHBOARD_FAB_SLOT_HEIGHT = 98.dp

// The single cutout definition, shared by the bar (top edge) and the hero (bottom edge, mirrored).
// The notch is a uniform 5dp outward offset of the 56dp FAB outline. For the gap to stay even around
// the FAB's rounded corners, the notch fillet must be *concentric* with the FAB corner — i.e. its
// radius = FAB corner (16) + clearance (5) = 21. Given this width and depth that lands exactly
// concentric, so the clearance no longer pinches at the corners.
internal val DASHBOARD_CUTOUT_TOP_CORNER_RADIUS = 24.dp
internal val DASHBOARD_CUTOUT_WIDTH = 66.dp
internal val DASHBOARD_CUTOUT_DEPTH = 27.dp

/** Concave fillet where each notch wall meets its floor: FAB corner radius (16) + 5dp clearance. */
internal val DASHBOARD_CUTOUT_INNER_RADIUS = 21.dp

/** Convex shoulder where the notch opens onto the flat edge. Must satisfy depth ≥ inner + outer. */
internal val DASHBOARD_CUTOUT_OUTER_RADIUS = 6.dp

/** Visible vertical gap between the hero card's bottom shoulders and the bar's top edge. */
internal val DASHBOARD_HERO_BAR_GAP = 12.dp

/**
 * Body height of the hero card above its bottom cradle notch, at font scale 1.0. Sized for the
 * worst case: headline (size + inline label) + caption + a [FlowRow] of tool chips wrapping to two
 * rows (all four tools) + the tap-hint wrapping to two lines. At larger font scales the card grows
 * via [dashboardHeroCardHeight] so the caption/hint don't clip — see that getter.
 */
internal val DASHBOARD_HERO_CONTENT_HEIGHT = 156.dp
internal val DASHBOARD_HERO_CARD_HEIGHT = DASHBOARD_HERO_CONTENT_HEIGHT + DASHBOARD_CUTOUT_DEPTH
internal val DASHBOARD_HERO_HORIZONTAL_MARGIN = 12.dp

/** Reserved dock height when the hero is shown: bar + gap + hero card (the FAB cradles between). */
internal val DASHBOARD_DOCK_HEIGHT_WITH_HERO =
    DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP + DASHBOARD_HERO_CARD_HEIGHT

/**
 * Upper bound on how far font scale stretches the hero card. The body height tracks text size so
 * the caption/hint stay readable; the cap only guards against pathological scales beyond the
 * platform range. 2.0 matches Android's maximum accessibility font size (200%), so the card still
 * fully covers the largest real text — the list above just reflows, the bar/FAB never move.
 */
private const val HERO_MAX_FONT_SCALE = 2.0f

/**
 * [DASHBOARD_HERO_CONTENT_HEIGHT] grown for the current font scale. The card's body is text, so its
 * height has to track the user's font size or the caption and the two-line tap-hint clip. Never
 * shrinks below the font-scale-1.0 worst-case height (coerced ≥ 1f).
 */
val dashboardHeroContentHeight: Dp
    @Composable
    @ReadOnlyComposable
    get() = DASHBOARD_HERO_CONTENT_HEIGHT * LocalDensity.current.fontScale.coerceIn(1f, HERO_MAX_FONT_SCALE)

/** Font-scale-aware counterpart of [DASHBOARD_HERO_CARD_HEIGHT] (content + fixed cradle notch). */
val dashboardHeroCardHeight: Dp
    @Composable
    @ReadOnlyComposable
    get() = dashboardHeroContentHeight + DASHBOARD_CUTOUT_DEPTH

/** Font-scale-aware counterpart of [DASHBOARD_DOCK_HEIGHT_WITH_HERO]. */
val dashboardDockHeightWithHero: Dp
    @Composable
    @ReadOnlyComposable
    get() = DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP + dashboardHeroCardHeight

/**
 * The bar's shape. The FAB only exists once the dashboard is [isReady]; until then the bar must NOT
 * carve out a cutout, or it would show an empty notch cradling nothing (e.g. during the initial
 * load). Bound to the exact same `isReady` flag that gates the FAB, so the two can never diverge.
 */
internal fun dashboardBarShape(isReady: Boolean): Shape = if (isReady) {
    DashboardBottomBarShape
} else {
    RoundedCornerShape(
        topStart = DASHBOARD_CUTOUT_TOP_CORNER_RADIUS,
        topEnd = DASHBOARD_CUTOUT_TOP_CORNER_RADIUS,
    )
}

/**
 * The bar: a rounded top with a centered cutout the FAB nestles into, square bottom corners.
 */
internal data object DashboardBottomBarShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfWidth = with(density) { (DASHBOARD_CUTOUT_WIDTH / 2).toPx() }
        val depth = with(density) { DASHBOARD_CUTOUT_DEPTH.toPx() }
        val innerR = with(density) { DASHBOARD_CUTOUT_INNER_RADIUS.toPx() }
        val outerR = with(density) { DASHBOARD_CUTOUT_OUTER_RADIUS.toPx() }
        val topR = with(density) { DASHBOARD_CUTOUT_TOP_CORNER_RADIUS.toPx() }
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

/**
 * The hero card: a fully rounded card whose bottom edge carries the **same** cutout as the bar's
 * top edge, mirrored — so the FAB cradles into both notches identically with a uniform gap.
 */
internal data object DashboardHeroCardShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfWidth = with(density) { (DASHBOARD_CUTOUT_WIDTH / 2).toPx() }
        val depth = with(density) { DASHBOARD_CUTOUT_DEPTH.toPx() }
        val innerR = with(density) { DASHBOARD_CUTOUT_INNER_RADIUS.toPx() }
        val outerR = with(density) { DASHBOARD_CUTOUT_OUTER_RADIUS.toPx() }
        val topR = with(density) { DASHBOARD_CUTOUT_TOP_CORNER_RADIUS.toPx() }
        val w = size.width
        val h = size.height
        val center = w / 2f
        val leftWall = (center - halfWidth).coerceAtLeast(topR + outerR)
        val rightWall = (center + halfWidth).coerceAtMost(w - topR - outerR)
        val path = Path().apply {
            // Top edge + rounded top corners.
            moveTo(topR, 0f)
            lineTo(w - topR, 0f)
            arcTo(
                rect = Rect(w - 2 * topR, 0f, w, 2 * topR),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            // Right edge down to the rounded bottom-right corner.
            lineTo(w, h - topR)
            arcTo(
                rect = Rect(w - 2 * topR, h - 2 * topR, w, h),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            // Bottom edge (right → left) with the mirrored cutout dipping UP into the card.
            lineTo(rightWall + outerR, h)
            arcTo(
                rect = Rect(rightWall, h - 2 * outerR, rightWall + 2 * outerR, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(rightWall, h - depth + innerR)
            arcTo(
                rect = Rect(rightWall - 2 * innerR, h - depth, rightWall, h - depth + 2 * innerR),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(leftWall + innerR, h - depth)
            arcTo(
                rect = Rect(leftWall, h - depth, leftWall + 2 * innerR, h - depth + 2 * innerR),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(leftWall, h - outerR)
            arcTo(
                rect = Rect(leftWall - 2 * outerR, h - 2 * outerR, leftWall, h),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            // Rounded bottom-left corner, left edge, rounded top-left corner.
            lineTo(topR, h)
            arcTo(
                rect = Rect(0f, h - 2 * topR, 2 * topR, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
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
