package eu.darken.sdmse.automation.core

import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

suspend fun AutomationManager.canUseAcsNow(): Boolean = useAcs.first()

suspend fun AutomationHost.waitForWindowRoot(delayMs: Long = 250): AccessibilityNodeInfo {
    var root: AccessibilityNodeInfo? = null

    while (currentCoroutineContext().isActive) {
        root = windowRoot()
        if (root != null) break

        if (Bugs.isDebug) log(VERBOSE) { "Waiting for windowRoot..." }
        delay(delayMs)
    }

    return root ?: throw CancellationException("Cancelled while waiting for windowRoot")
}