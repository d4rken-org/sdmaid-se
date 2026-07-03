package eu.darken.sdmse.automation.core.common.stepper

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.ViewConfiguration
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.children
import eu.darken.sdmse.automation.core.common.contentDescMatches
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.distanceTo
import eu.darken.sdmse.automation.core.common.findParentOrNull
import eu.darken.sdmse.automation.core.common.isEmpty
import eu.darken.sdmse.automation.core.common.textMatches
import eu.darken.sdmse.automation.core.dispatchGesture
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.errors.UnclickableTargetException
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull


suspend fun StepContext.findNode(
    predicate: suspend (ACSNodeInfo) -> Boolean
): ACSNodeInfo? = host.waitForWindowRoot().crawl().map { it.node }.firstOrNull { predicate(it) }

/**
 * Finds a node by iterating through labels in priority order.
 * Unlike [findNode] with `textMatchesAny`, this ensures the first label in the list
 * has priority over subsequent labels, regardless of node position in the tree.
 *
 * This is important for localization fallbacks where specific labels should be preferred.
 */
suspend fun StepContext.findNodeByLabel(
    labels: Collection<String>,
    predicate: (ACSNodeInfo) -> Boolean = { true },
): ACSNodeInfo? {
    val tree = host.waitForWindowRoot().crawl().map { it.node }.toList()
    return labels.firstNotNullOfOrNull { label ->
        tree.find { it.textMatches(label) && predicate(it) }
    }
}

/**
 * Finds a node by content description, iterating through labels in priority order.
 * Similar to [findNodeByLabel] but matches against contentDescription instead of text.
 *
 * This is useful on Android 16+ where button labels may be in content-desc rather than text.
 */
suspend fun StepContext.findNodeByContentDesc(
    labels: Collection<String>,
    predicate: (ACSNodeInfo) -> Boolean = { true },
): ACSNodeInfo? {
    val tree = host.waitForWindowRoot().crawl().map { it.node }.toList()
    return labels.firstNotNullOfOrNull { label ->
        tree.find { it.contentDescMatches(label) && predicate(it) }
    }
}

data class FocusState(
    val inputFocused: ACSNodeInfo?,
    val accessibilityFocused: ACSNodeInfo?,
)

/**
 * Finds the currently focused nodes using the accessibility framework's live focus query.
 */
suspend fun StepContext.findFocusedNode(): FocusState {
    val root = host.windowRoot() ?: return FocusState(null, null)
    return FocusState(
        inputFocused = root.findFocus(ACSNodeInfo.FOCUS_INPUT),
        accessibilityFocused = root.findFocus(ACSNodeInfo.FOCUS_ACCESSIBILITY),
    )
}

/**
 * Waits for the layout to stabilize by polling an anchor node's bounds.
 * Returns the anchor node once bounds are stable across two consecutive checks, or null if
 * the anchor is never found or bounds never stabilize within [maxChecks] iterations.
 */
suspend fun StepContext.waitForLayoutStability(
    anchorId: String,
    maxChecks: Int = 10,
    delayMs: Long = 200,
): ACSNodeInfo? {
    var lastBounds: ACSNodeInfo.ScreenBounds? = null
    for (i in 1..maxChecks) {
        val node = host.windowRoot()?.crawl()?.map { it.node }
            ?.firstOrNull { it.viewIdResourceName == anchorId }
        if (node == null) {
            log(tag, INFO) { "Stabilize check #$i: anchor '$anchorId' not found yet" }
            delay(delayMs)
            continue
        }
        val bounds = node.getScreenBounds()
        log(tag) { "Stabilize check #$i: bounds=$bounds" }
        if (bounds == lastBounds) {
            log(tag, INFO) { "Layout stabilized after $i checks" }
            return node
        }
        lastBounds = bounds
        delay(delayMs)
    }
    return null
}

suspend fun StepContext.findClickableParent(
    maxNesting: Int = 6,
    includeSelf: Boolean = false,
    node: ACSNodeInfo,
): ACSNodeInfo? = if (includeSelf && node.isClickable) {
    node
} else {
    node.findParentOrNull(maxNesting = maxNesting) {
        log(tag, VERBOSE) { "isClickable? $it" }
        it.isClickable
    }
}

suspend fun StepContext.findClickableSibling(
    maxNesting: Int = 1,
    includeSelf: Boolean = false,
    node: ACSNodeInfo,
): ACSNodeInfo? {
    if (includeSelf && node.isClickable) {
        return node
    }

    var currentParent = node.parent ?: return null

    repeat(maxNesting) {
        val clickableSibling = currentParent.children().firstOrNull { sibling ->
            if (sibling == node) {
                false
            } else {
                log(tag, VERBOSE) { "isClickable sibling? $sibling" }
                sibling.isClickable
            }
        }

        if (clickableSibling != null) return clickableSibling

        currentParent = currentParent.parent ?: return null
    }

    return null
}

suspend fun StepContext.findNearestTo(
    maxNesting: Int = 1,
    includeSelf: Boolean = false,
    node: ACSNodeInfo,
    predicate: suspend (ACSNodeInfo) -> Boolean = { true }
): ACSNodeInfo? {
    log(tag, VERBOSE) { "findNearestTo(max=$maxNesting, self=$includeSelf, node=$node): Searching..." }
    if (includeSelf && predicate(node)) return node

    var currentParent = node.parent ?: run {
        log(tag, WARN) { "findNearestTo: Node has no parent, cannot find siblings: $node" }
        return null
    }
    var nearestNode: ACSNodeInfo? = null
    var minDistance = Double.MAX_VALUE
    val ancestors = mutableSetOf(node)

    repeat(maxNesting) {
        ancestors.add(currentParent)

        currentParent.children().forEach { sibling ->
            if (sibling !in ancestors && predicate(sibling)) {
                val distance = node.distanceTo(sibling)
                log(tag, VERBOSE) { "findNearestTo: Distance ${distance}px to sibling $sibling" }
                if (distance < minDistance) {
                    minDistance = distance
                    nearestNode = sibling
                }
            }
        }

        currentParent = currentParent.parent ?: return@repeat
    }

    return nearestNode
}

/**
 * Finds the clickable node that visually *owns* [label] in a row of icon+label action buttons
 * (e.g. the AOSP App-Info top action row: Archive / Uninstall / Force stop). On Android 15+ the
 * icon and label are separate, unclickable nodes and the tappable wrapper is a sibling; picking
 * the merely-nearest clickable ([findNearestTo]) would grab an adjacent action (e.g. the Uninstall
 * button for a Force-stop label). This restricts the match to a clickable that sits in the SAME
 * column as the label: it horizontally contains the label's center-X, is vertically related to the
 * label (overlapping it, or directly above/below within ~2 label-heights — covering both the
 * icon-over-label grid and the button-over-text list layouts), and is not wide enough to span
 * multiple action columns.
 *
 * Returns null when no such clickable exists. On this action row a button exposes a clickable
 * wrapper only while enabled, so absence usually means the action is disabled — callers decide
 * what that means.
 */
suspend fun StepContext.findColumnAlignedClickable(
    label: ACSNodeInfo,
    maxNesting: Int = 3,
): ACSNodeInfo? {
    val labelBounds = label.getScreenBounds()
    if (labelBounds.isEmpty()) {
        log(tag, WARN) { "findColumnAlignedClickable: label bounds empty/degenerate: $label" }
        return null
    }
    val labelCenterX = (labelBounds.left + labelBounds.right) / 2
    val labelHeight = labelBounds.bottom - labelBounds.top
    // A single action cell is well under half the row width; reject a full-width clickable that
    // would span several columns (and thus horizontally contain multiple action labels).
    val maxTargetWidth = host.windowRoot()?.getScreenBounds()
        ?.let { it.right - it.left }
        ?.takeIf { it > 0 }
        ?.let { it / 2 }
        ?: Int.MAX_VALUE

    return findNearestTo(maxNesting = maxNesting, node = label) { candidate ->
        if (!candidate.isClickable) return@findNearestTo false
        val b = candidate.getScreenBounds()
        if (b.isEmpty()) return@findNearestTo false
        val containsCenterX = labelCenterX in b.left until b.right
        // Same cell: the wrapper overlaps the label, or sits directly above/below it. The layout
        // varies (grid cell spanning icon+label vs. a button stacked over its text), so accept both
        // overlap and tight adjacency. Full-width rows that happen to be adjacent are excluded by
        // the width cap below.
        val overlapsY = b.top < labelBounds.bottom && b.bottom > labelBounds.top
        val adjacentAbove = (labelBounds.top - b.bottom) in 0..(2 * labelHeight)
        val adjacentBelow = (b.top - labelBounds.bottom) in 0..(2 * labelHeight)
        val verticallyRelated = overlapsY || adjacentAbove || adjacentBelow
        val singleColumn = (b.right - b.left) <= maxTargetWidth
        containsCenterX && verticallyRelated && singleColumn
    }
}

fun StepContext.clickNormal(
    isDryRun: Boolean = false,
    node: ACSNodeInfo,
): Boolean {
    log(tag, VERBOSE) { "clickNormal(isDryRun=$isDryRun): Clicking on $node" }

    return when {
        !node.isEnabled -> throw DisabledTargetException("Clickable target is disabled.")
        isDryRun -> node.performAction(ACSNodeInfo.ACTION_SELECT)
        node.isClickable -> node.performAction(ACSNodeInfo.ACTION_CLICK)
        else -> throw UnclickableTargetException("Target is not clickable")
    }
}

suspend fun StepContext.clickGesture(
    isDryRun: Boolean = false,
    node: ACSNodeInfo,
): Boolean {
    val bounds = node.getScreenBounds()
    // Empty/degenerate bounds mean the node is clipped (e.g. behind a system bar). The
    // computed center would land on whatever occupies that region (often the navigation bar's
    // home button), so refuse the tap and let the step retry via node recovery.
    if (bounds.isEmpty()) {
        log(tag, WARN) { "clickGesture(): Refusing tap, node bounds are empty/degenerate: $bounds ($node)" }
        return false
    }
    val x = ((bounds.left + bounds.right) / 2).toFloat()
    val y = ((bounds.top + bounds.bottom) / 2).toFloat()
    log(tag, VERBOSE) { "clickGesture(): node=$node, bounds=$bounds" }
    return clickGestureAtCoords(x, y, isDryRun)
}

/**
 * Performs a gesture click at specific screen coordinates.
 * Used when target nodes are hidden from accessibility tree but position is known.
 */
suspend fun StepContext.clickGestureAtCoords(
    x: Float,
    y: Float,
    isDryRun: Boolean = false,
): Boolean {
    // Defense-in-depth: never dispatch a tap outside the current foreground window (e.g. onto
    // the navigation/status bar, which belongs to a different window). right/bottom are exclusive.
    host.windowRoot()?.getScreenBounds()?.let { root ->
        if (x < root.left || x >= root.right || y < root.top || y >= root.bottom) {
            log(tag, WARN) { "clickGestureAtCoords(): Refusing tap at X=$x, Y=$y, outside window root $root" }
            return false
        }
    }

    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + 1f, y + 1f)
    }
    val gesture = GestureDescription.Builder().apply {
        addStroke(GestureDescription.StrokeDescription(path, 0, ViewConfiguration.getTapTimeout().toLong()))
    }.build()

    return try {
        log(tag, VERBOSE) { "clickGesture(): Waiting for passthrough..." }
        host.changeOptions { it.copy(passthrough = true) }
        host.state.filter { it.passthrough }.first()

        log(tag) { "clickGestureAtCoords(): Performing CLICK gesture at X=$x, Y=$y" }
        if (isDryRun) true else host.dispatchGesture(gesture)
    } finally {
        // Always restore passthrough, even if enabling/dispatch threw or we're cancelled
        // mid-gesture; leaving it on would stop the service from intercepting touches. Bounded
        // so it can never hang past the step timeout if the state never settles.
        withContext(NonCancellable) {
            host.changeOptions { it.copy(passthrough = false) }
            withTimeoutOrNull(1_000L) { host.state.filter { !it.passthrough }.first() }
        }
    }
}
