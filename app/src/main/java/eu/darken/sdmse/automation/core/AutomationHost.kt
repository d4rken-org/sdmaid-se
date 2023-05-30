package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.progress.Progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface AutomationHost : Progress.Client {

    val service: AccessibilityService

    val scope: CoroutineScope

    suspend fun windowRoot(): AccessibilityNodeInfo

    suspend fun changeOptions(action: (Options) -> Options)

    val events: Flow<AccessibilityEvent>

    data class Options(
        val showOverlay: Boolean = true,
        val panelGravity: Int = Gravity.BOTTOM,
        val accessibilityServiceInfo: AccessibilityServiceInfo = AccessibilityServiceInfo(),
        val controlPanelTitle: CaString = R.string.automation_active_title.toCaString(),
        val controlPanelSubtitle: CaString = eu.darken.sdmse.common.R.string.general_progress_loading.toCaString(),
    ) {
        /*
            java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String android.content.ComponentName.flattenToShortString()' on a null object reference
            at android.accessibilityservice.AccessibilityServiceInfo.getId(AccessibilityServiceInfo.java:759)
            at android.accessibilityservice.AccessibilityServiceInfo.toString(AccessibilityServiceInfo.java:1105)
            at java.lang.String.valueOf(String.java:2924)
            at java.lang.StringBuilder.append(StringBuilder.java:132)
            at eu.darken.sdmse.automation.core.AccessibilityConfig.toString(Unknown Source:12)
        */
        override fun toString(): String = try {
            super.toString()
        } catch (e: Exception) {
            "AutomationHost.Options(accessibilityServiceInfo=ERROR, showOverlay=$showOverlay, panelGravity=$panelGravity)"
        }
    }
}