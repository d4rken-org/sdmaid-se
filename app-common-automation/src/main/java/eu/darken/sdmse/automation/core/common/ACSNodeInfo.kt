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
    val isFocused: Boolean
    val isAccessibilityFocused: Boolean
    val childCount: Int

    // Navigation
    val parent: ACSNodeInfo?

    // Methods
    fun getChild(index: Int): ACSNodeInfo?
    fun performAction(action: Int): Boolean
    fun refresh(): Boolean
    fun getBoundsInScreen(outBounds: Rect)
    fun getScreenBounds(): ScreenBounds
    fun findFocus(focusType: Int): ACSNodeInfo?

    data class ScreenBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    companion object {
        // Action constants
        const val ACTION_CLICK = AccessibilityNodeInfo.ACTION_CLICK
        const val ACTION_SELECT = AccessibilityNodeInfo.ACTION_SELECT
        const val ACTION_SCROLL_FORWARD = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        const val ACTION_FOCUS = AccessibilityNodeInfo.ACTION_FOCUS
        const val ACTION_ACCESSIBILITY_FOCUS = AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS

        const val FOCUS_INPUT = AccessibilityNodeInfo.FOCUS_INPUT
        const val FOCUS_ACCESSIBILITY = AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
    }
}
