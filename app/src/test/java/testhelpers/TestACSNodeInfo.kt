package testhelpers

import android.graphics.Rect
import eu.darken.sdmse.automation.core.common.ACSNodeInfo

data class TestACSNodeInfo(
    override val text: CharSequence? = null,
    override val className: CharSequence? = null,
    override val packageName: CharSequence? = null,
    override val viewIdResourceName: String? = null,
    override val isClickable: Boolean = false,
    override val isEnabled: Boolean = true,
    override val isCheckable: Boolean = false,
    override val isScrollable: Boolean = false,
    private val children: MutableList<TestACSNodeInfo> = mutableListOf(),
    private var parentNode: TestACSNodeInfo? = null
) : ACSNodeInfo {

    override val childCount: Int get() = children.size
    override val parent: ACSNodeInfo? get() = parentNode

    override fun getChild(index: Int): ACSNodeInfo? = children.getOrNull(index)

    override fun performAction(action: Int): Boolean = true
    override fun refresh(): Boolean = true
    override fun getBoundsInScreen(outBounds: Rect) {
        // Simple implementation for testing - set to a default rectangle
        outBounds.set(0, 0, 100, 50)
    }

    fun addChild(child: TestACSNodeInfo): TestACSNodeInfo {
        children.add(child)
        child.parentNode = this
        return this
    }

    fun addChildren(vararg children: TestACSNodeInfo): TestACSNodeInfo {
        children.forEach { addChild(it) }
        return this
    }

    override fun toString(): String {
        return "TestACSNodeInfo(text='$text', isClickable=$isClickable, childCount=$childCount, hasParent=${parent != null})"
    }

    companion object {
        fun clickableNode(text: String? = null) = TestACSNodeInfo(
            text = text,
            isClickable = true
        )

        fun textNode(text: String) = TestACSNodeInfo(
            text = text,
            className = "android.widget.TextView"
        )

        fun buttonNode(text: String) = TestACSNodeInfo(
            text = text,
            className = "android.widget.Button",
            isClickable = true
        )
    }
}