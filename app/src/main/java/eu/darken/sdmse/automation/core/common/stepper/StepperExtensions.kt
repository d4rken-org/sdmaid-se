package eu.darken.sdmse.automation.core.common.stepper

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.children
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.findParentOrNull
import eu.darken.sdmse.automation.core.dispatchGesture
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.errors.UnclickableTargetException
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first


suspend fun StepContext.findNode(
    predicate: suspend (ACSNodeInfo) -> Boolean
): ACSNodeInfo? = host.waitForWindowRoot().crawl().map { it.node }.firstOrNull { predicate(it) }

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

    log(tag) { "clickGesture(): Performing CLICK gesture at X=$x,Y=$y" }
    val success = if (isDryRun) true else host.dispatchGesture(gesture)

    host.changeOptions { it.copy(passthrough = false) }
    host.state.filter { !it.passthrough }.first()

    return success
}
