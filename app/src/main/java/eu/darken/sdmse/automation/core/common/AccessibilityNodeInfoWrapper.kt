package eu.darken.sdmse.automation.core.common

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

private class AccessibilityNodeInfoWrapper(
    val node: AccessibilityNodeInfo
) : ACSNodeInfo {

    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val className: CharSequence? get() = node.className
    override val packageName: CharSequence? get() = node.packageName
    override val viewIdResourceName: String? get() = node.viewIdResourceName

    override val isClickable: Boolean get() = node.isClickable
    override val isEnabled: Boolean get() = node.isEnabled
    override val isCheckable: Boolean get() = node.isCheckable
    override val isScrollable: Boolean get() = node.isScrollable
    override val childCount: Int get() = node.childCount

    override val parent: ACSNodeInfo? get() = node.parent?.toNodeInfo()

    override fun getChild(index: Int): ACSNodeInfo? = node.getChild(index)?.toNodeInfo()

    override fun performAction(action: Int): Boolean = node.performAction(action)

    override fun refresh(): Boolean = node.refresh()

    override fun getBoundsInScreen(outBounds: Rect) = node.getBoundsInScreen(outBounds)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccessibilityNodeInfoWrapper) return false
        return node == other.node
    }

    override fun hashCode(): Int = node.hashCode()

    override fun toString(): String {
        val identity = Integer.toHexString(System.identityHashCode(this))
        val bounds = Rect().apply { getBoundsInScreen(this) }
        return "text='${this.text}', contentDesc='${this.contentDescription}', class=${this.className}, clickable=$isClickable, checkable=$isCheckable enabled=$isEnabled, id=$viewIdResourceName pkg=$packageName, identity=$identity, bounds=$bounds"
    }
}

fun AccessibilityNodeInfo.toNodeInfo(): ACSNodeInfo = AccessibilityNodeInfoWrapper(this)
