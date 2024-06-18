package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.main.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

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

suspend fun AutomationModule.finishAutomation(
    // If we aborted due to an exception and the reason is "User has cancelled", then still clean up
    userCancelled: Boolean,
    returnToApp: Boolean
) = withContext(if (userCancelled) NonCancellable else EmptyCoroutineContext) {
    if (returnToApp) {
        log(INFO) { "finishAutomation(...): Returning to SD Maid" }
        val returnIntern = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        context.startActivity(returnIntern)
    } else {
        log(INFO) { "finishAutomation(...): Going to home screen" }
        host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}