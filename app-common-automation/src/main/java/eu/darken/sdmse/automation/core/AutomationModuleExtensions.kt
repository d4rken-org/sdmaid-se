package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

private val TAG = logTag("Automation", "Module", "Extensions")

suspend fun AutomationModule.finishAutomation(
    userCancelled: Boolean,
    returnToAppIntent: Intent?,
    deviceDetective: DeviceDetective,
) = withContext(if (userCancelled) NonCancellable else EmptyCoroutineContext) {
    if (returnToAppIntent != null) {
        // Settings may have multiple screens open (e.g. App Info → Storage).
        // Press BACK until we're back at SD Maid, closing all settings screens along the way.
        log(TAG, INFO) { "finishAutomation(...): Pressing BACK to return to SD Maid" }
        for (attempt in 1..10) {
            val currentPkg = host.windowRoot()?.packageName?.toString()
            if (currentPkg == context.packageName) {
                log(TAG, INFO) { "finishAutomation(...): Back at SD Maid after ${attempt - 1} BACK press(es)" }
                break
            }
            log(TAG, VERBOSE) { "finishAutomation(...): At $currentPkg, pressing BACK (attempt $attempt)" }
            host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(200)
        }
        // Fallback: if BACK didn't get us back, use intent navigation
        if (host.windowRoot()?.packageName?.toString() != context.packageName) {
            log(TAG, INFO) { "finishAutomation(...): BACK loop didn't return to SD Maid, using intent" }
            context.startActivity(returnToAppIntent)
        }
    } else {
        when (deviceDetective.getROMType()) {
            RomType.ANDROID_TV -> {
                log(TAG, INFO) { "finishAutomation(...): Going back via back button" }
                val backAction = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                log(TAG, VERBOSE) { "finishAutomation(...): Back button successful=$backAction" }
            }

            else -> {
                log(TAG, INFO) { "finishAutomation(...): Going to home screen" }
                host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
    }
}
