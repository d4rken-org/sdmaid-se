package eu.darken.sdmse.automation.core.common

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

interface ACSNodeInfo {

    // Basic properties
    val text: CharSequence?
    val contentDescription: CharSequence?
    val className: CharSequence?
    val packageName: CharSequence?
    val viewIdResourceName: String?

    // State properties
    val isClickable: Boolean
    val isEnabled: Boolean
    val isCheckable: Boolean
    val isScrollable: Boolean
    val childCount: Int

    // Navigation
    val parent: ACSNodeInfo?

    // Methods
    fun getChild(index: Int): ACSNodeInfo?
    fun performAction(action: Int): Boolean
    fun refresh(): Boolean
    fun getBoundsInScreen(outBounds: Rect)

    companion object {
        // Action constants
        const val ACTION_CLICK = AccessibilityNodeInfo.ACTION_CLICK
        const val ACTION_SELECT = AccessibilityNodeInfo.ACTION_SELECT
        const val ACTION_SCROLL_FORWARD = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
    }
}