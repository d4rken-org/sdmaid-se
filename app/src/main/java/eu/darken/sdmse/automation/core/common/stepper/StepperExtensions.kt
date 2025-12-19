package eu.darken.sdmse.automation.core.common.stepper

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.children
import eu.darken.sdmse.automation.core.common.contentDescMatches
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.distanceTo
import eu.darken.sdmse.automation.core.common.findParentOrNull
import eu.darken.sdmse.automation.core.common.textMatches
import eu.darken.sdmse.automation.core.dispatchGesture
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.errors.UnclickableTargetException
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first


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
    val rect = Rect().apply { node.getBoundsInScreen(this) }
    val x = rect.centerX().toFloat()
    val y = rect.centerY().toFloat()
    log(tag, VERBOSE) { "clickGesture(): node=$node, bounds=$rect" }
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
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + 1f, y + 1f)
    }
    val gesture = GestureDescription.Builder().apply {
        addStroke(GestureDescription.StrokeDescription(path, 0, ViewConfiguration.getTapTimeout().toLong()))
    }.build()

    log(tag, VERBOSE) { "clickGesture(): Waiting for passthrough..." }
    host.changeOptions { it.copy(passthrough = true) }
    host.state.filter { it.passthrough }.first()

    log(tag) { "clickGestureAtCoords(): Performing CLICK gesture at X=$x, Y=$y" }
    val success = if (isDryRun) true else host.dispatchGesture(gesture)

    host.changeOptions { it.copy(passthrough = false) }
    host.state.filter { !it.passthrough }.first()

    return success
}
