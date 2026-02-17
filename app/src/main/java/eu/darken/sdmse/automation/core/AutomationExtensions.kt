package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.main.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

suspend fun AutomationManager.canUseAcsNow(): Boolean = useAcs.first()

suspend fun AutomationHost.waitForWindowRoot(delayMs: Long = 250): ACSNodeInfo {
    var root: ACSNodeInfo? = null

    while (currentCoroutineContext().isActive) {
        root = windowRoot()
        if (root != null) break

        if (Bugs.isDebug) log(VERBOSE) { "Waiting for windowRoot..." }
        delay(delayMs)
    }

    return root ?: throw CancellationException("Cancelled while waiting for windowRoot")
}

suspend fun AutomationHost.dispatchGesture(gesture: GestureDescription): Boolean =
    suspendCancellableCoroutine { cont ->
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }, null)
    }

suspend fun AutomationModule.finishAutomation(
    // If we aborted due to an exception and the reason is "User has cancelled", then still clean up
    userCancelled: Boolean,
    returnToApp: Boolean,
    deviceDetective: DeviceDetective,
) = withContext(if (userCancelled) NonCancellable else EmptyCoroutineContext) {
    if (returnToApp) {
        // Settings may have multiple screens open (e.g. App Info → Storage).
        // Press BACK until we're back at SD Maid, closing all settings screens along the way.
        log(INFO) { "finishAutomation(...): Pressing BACK to return to SD Maid" }
        for (attempt in 1..10) {
            val currentPkg = host.windowRoot()?.packageName?.toString()
            if (currentPkg == context.packageName) {
                log(INFO) { "finishAutomation(...): Back at SD Maid after ${attempt - 1} BACK press(es)" }
                break
            }
            log(VERBOSE) { "finishAutomation(...): At $currentPkg, pressing BACK (attempt $attempt)" }
            host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(200)
        }
        // Fallback: if BACK didn't get us back, use intent navigation
        if (host.windowRoot()?.packageName?.toString() != context.packageName) {
            log(INFO) { "finishAutomation(...): BACK loop didn't return to SD Maid, using intent" }
            val returnIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(returnIntent)
        }
    } else {
        when (deviceDetective.getROMType()) {
            RomType.ANDROID_TV -> {
                log(INFO) { "finishAutomation(...): Going back via back button" }
                val backAction = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                log(VERBOSE) { "finishAutomation(...): Back button successful=$backAction" }
            }

            else -> {
                log(INFO) { "finishAutomation(...): Going to home screen" }
                host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
    }
}