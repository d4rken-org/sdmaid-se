package eu.darken.sdmse.automation.core.common.stepper

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.findParentOrNull
import eu.darken.sdmse.automation.core.common.toStringShort
import eu.darken.sdmse.automation.core.dispatchGesture
import eu.darken.sdmse.automation.core.errors.DisabledTargetException
import eu.darken.sdmse.automation.core.errors.UnclickableTargetException
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first


suspend fun StepContext.findNode(
    predicate: suspend (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? = host.waitForWindowRoot().crawl().map { it.node }.firstOrNull { predicate(it) }

suspend fun StepContext.findClickableParent(
    maxNesting: Int = 6,
    includeSelf: Boolean = false,
    node: AccessibilityNodeInfo,
): AccessibilityNodeInfo? = if (includeSelf && node.isClickable) {
    node
} else {
    node.findParentOrNull(maxNesting = maxNesting) {
        log(tag, VERBOSE) { "isClickable? ${it.toStringShort()}" }
        it.isClickable
    }
}


fun StepContext.clickNormal(
    isDryRun: Boolean = false,
    node: AccessibilityNodeInfo,
): Boolean {
    log(tag, VERBOSE) { "clickNormal(isDryRun=$isDryRun): Clicking on ${node.toStringShort()}" }

    return when {
        !node.isEnabled -> throw DisabledTargetException("Clickable target is disabled.")
        isDryRun -> node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        node.isClickable -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        else -> throw UnclickableTargetException("Target is not clickable")
    }
}

suspend fun StepContext.clickGesture(
    isDryRun: Boolean = false,
    node: AccessibilityNodeInfo,
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
