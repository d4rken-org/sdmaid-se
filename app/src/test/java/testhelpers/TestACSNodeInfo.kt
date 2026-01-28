package testhelpers

import android.graphics.Rect
import eu.darken.sdmse.automation.core.common.ACSNodeInfo

data class TestACSNodeInfo(
    override val text: CharSequence? = null,
    override val contentDescription: CharSequence? = null,
    override val className: CharSequence? = null,
    override val packageName: CharSequence? = null,
    override val viewIdResourceName: String? = null,
    override val isClickable: Boolean = false,
    override val isEnabled: Boolean = true,
    override val isCheckable: Boolean = false,
    override val isScrollable: Boolean = false,
    private val children: MutableList<TestACSNodeInfo> = mutableListOf(),
    private var parentNode: TestACSNodeInfo? = null,
    private val childrenArray: Array<ACSNodeInfo?>? = null,
    private val bounds: Rect = Rect(0, 0, 100, 50)
) : ACSNodeInfo {

    override val childCount: Int get() = childrenArray?.size ?: children.size
    override val parent: ACSNodeInfo? get() = parentNode

    override fun getChild(index: Int): ACSNodeInfo? =
        childrenArray?.getOrNull(index) ?: children.getOrNull(index)

    private val _performedActions = mutableListOf<Int>()
    val performedActions: List<Int> get() = _performedActions.toList()

    override fun performAction(action: Int): Boolean {
        _performedActions.add(action)
        return true
    }

    fun clearPerformedActions() {
        _performedActions.clear()
    }
    override fun refresh(): Boolean = true
    override fun getBoundsInScreen(outBounds: Rect) {
        outBounds.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
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
        return "TestACSNodeInfo(text='$text', contentDesc='$contentDescription', isClickable=$isClickable, childCount=$childCount, hasParent=${parent != null})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestACSNodeInfo) return false
        return text == other.text &&
                contentDescription == other.contentDescription &&
                className == other.className &&
                packageName == other.packageName &&
                viewIdResourceName == other.viewIdResourceName &&
                isClickable == other.isClickable &&
                isEnabled == other.isEnabled &&
                isCheckable == other.isCheckable &&
                isScrollable == other.isScrollable &&
                children == other.children &&
                childrenArray?.contentEquals(other.childrenArray) == true
    }

    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (contentDescription?.hashCode() ?: 0)
        result = 31 * result + (className?.hashCode() ?: 0)
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (viewIdResourceName?.hashCode() ?: 0)
        result = 31 * result + isClickable.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + isCheckable.hashCode()
        result = 31 * result + isScrollable.hashCode()
        result = 31 * result + children.hashCode()
        result = 31 * result + (childrenArray?.contentHashCode() ?: 0)
        return result
    }
}