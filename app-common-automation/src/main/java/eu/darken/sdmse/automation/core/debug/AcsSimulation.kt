package eu.darken.sdmse.automation.core.debug

import android.annotation.SuppressLint
import android.graphics.Rect
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug builds only: lets an emulator stand in for device behaviour it doesn't have.
 *
 * `adb shell setprop debug.sdmse.acs.sim hide_storage_buttons,freeze_cache_stats`
 * - `hide_storage_buttons`: Settings' storage action buttons are left out of every window root, as a
 *   Pixel on Android 17 leaves them out of the accessibility tree.
 * - `freeze_cache_stats`: every cache stats read returns the package's first answer.
 */
object AcsSimulation {

    private const val PROP = "debug.sdmse.acs.sim"
    private const val SETTINGS_PKG = "com.android.settings"
    private val STORAGE_ACTION_IDS = (1..4).map { "$SETTINGS_PKG:id/action$it" }.toSet()

    private val frozenStats = ConcurrentHashMap<String, Long>()

    private fun flags(): Set<String> {
        if (!BuildConfigWrap.DEBUG) return emptySet()
        val raw = try {
            @SuppressLint("PrivateApi")
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getMethod("get", String::class.java).invoke(null, PROP) as String
        } catch (e: Exception) {
            ""
        }
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun windowRoot(root: ACSNodeInfo): ACSNodeInfo {
        if ("hide_storage_buttons" !in flags()) return root
        return HidingNode(root) { it.packageName == SETTINGS_PKG && it.viewIdResourceName in STORAGE_ACTION_IDS }
    }

    fun cacheStats(pkg: String, bytes: Long?): Long? {
        if ("freeze_cache_stats" !in flags() || bytes == null) return bytes
        val frozen = frozenStats.getOrPut(pkg) { bytes }
        if (frozen != bytes) log(TAG, INFO) { "freeze_cache_stats: $pkg read $bytes, answering $frozen" }
        return frozen
    }

    private class HidingNode(
        private val node: ACSNodeInfo,
        private val hidden: (ACSNodeInfo) -> Boolean,
    ) : ACSNodeInfo by node {

        private var cachedChildren: List<ACSNodeInfo>? = null
        private val visibleChildren: List<ACSNodeInfo>
            get() = cachedChildren
                ?: (0 until node.childCount).mapNotNull { node.getChild(it) }.filterNot(hidden).also { cachedChildren = it }

        override fun refresh(): Boolean = node.refresh().also { cachedChildren = null }

        override val childCount: Int get() = visibleChildren.size

        override val parent: ACSNodeInfo? get() = node.parent?.let { HidingNode(it, hidden) }

        override fun getChild(index: Int): ACSNodeInfo? = visibleChildren.getOrNull(index)?.let { HidingNode(it, hidden) }

        override fun findFocus(focusType: Int): ACSNodeInfo? {
            val focused = node.findFocus(focusType) ?: return null
            var current: ACSNodeInfo? = focused
            while (current != null) {
                if (hidden(current)) {
                    log(TAG, INFO) { "hide_storage_buttons: hiding focus($focusType) on $focused" }
                    return null
                }
                current = current.parent
            }
            return HidingNode(focused, hidden)
        }

        override fun getBoundsInScreen(outBounds: Rect) = node.getBoundsInScreen(outBounds)

        override fun equals(other: Any?): Boolean = other is HidingNode && node == other.node

        override fun hashCode(): Int = node.hashCode()

        override fun toString(): String = node.toString()
    }

    private val TAG = logTag("Automation", "Debug", "AcsSimulation")
}
