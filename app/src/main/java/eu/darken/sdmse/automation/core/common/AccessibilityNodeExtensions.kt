package eu.darken.sdmse.automation.core.common

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.concurrent.LinkedBlockingDeque

fun AccessibilityNodeInfo.toStringShort() =
    "className=${this.className}, text='${this.text}', isClickable=${this.isClickable}, isEnabled=${this.isEnabled}, viewIdResourceName=${this.viewIdResourceName}, pkgName=${this.packageName}"

val AccessibilityNodeInfo.textVariants: Set<String>
    get() {
        val target = text?.toString() ?: return emptySet()

        return setOf(
            target,
            target.replace(' ', ' ')
        )
    }

fun AccessibilityNodeInfo.textMatchesAny(candidates: Collection<String>): Boolean {
    candidates.forEach { candidate ->
        if (textVariants.any { it.equals(candidate, ignoreCase = true) }) {
            return true
        }
    }
    return false
}

fun AccessibilityNodeInfo.textEndsWithAny(candidates: Collection<String>): Boolean {
    candidates.forEach { candidate ->
        if (textVariants.any { it.endsWith(candidate, ignoreCase = true) }) {
            return true
        }
    }
    return false
}

fun AccessibilityNodeInfo.idMatches(id: String): Boolean {
    return viewIdResourceName == id
}

fun AccessibilityNodeInfo.idContains(id: String): Boolean {
    return viewIdResourceName?.contains(id) == true
}

fun AccessibilityNodeInfo.isClickyButton(): Boolean {
    return isClickable && className == "android.widget.Button"
}

fun AccessibilityNodeInfo.isTextView(): Boolean {
    return className == "android.widget.TextView"
}

fun AccessibilityNodeInfo.children() = (0 until childCount).mapNotNull { getChild(it) }

fun AccessibilityNodeInfo.searchUp(
    maxNesting: Int = 1,
    predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo {
    var curParent = this.parent

    for (i in 0 until maxNesting) {
        val desiredSibling = curParent.children().firstOrNull { relative ->
            if (relative == this) {
                false
            } else {
                predicate(relative)
            }
        }
        if (desiredSibling != null) return desiredSibling

        if (curParent.parent != null) curParent = curParent.parent
    }

    throw AutomationException("No sibling found.")
}

fun AccessibilityNodeInfo.findParentOrNull(
    maxNesting: Int = 3,
    predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    var target = this
    for (i in 1..maxNesting) {
        if (predicate(target)) return target
        if (target.parent != null) target = target.parent
    }
    return null
}

fun AccessibilityNodeInfo.getRoot(maxNesting: Int = 15 /*AOSP*/): AccessibilityNodeInfo {
    var target: AccessibilityNodeInfo = this
    for (i in 1..maxNesting) {
        target.parent?.let {
            target = it
        } ?: break
    }
    return target
}

fun AccessibilityNodeInfo.crawl(debug: Boolean = false): Sequence<CrawledNode> = sequence {
    try {
        if (this@crawl.getChild(0) == null) {
            this@crawl.refresh().let { log(CrawlerCommon.TAG) { "Refresh success: $it" } }
        }
    } catch (e: Exception) {
        log(CrawlerCommon.TAG, WARN) { "Null crawl failed to refresh: ${e.asLog()}" }
    }

    val queue = LinkedBlockingDeque<CrawledNode>()
    queue.add(CrawledNode(this@crawl, 0))

    while (!queue.isEmpty()) {
        val cur = queue.poll()!!

        if (debug) log(CrawlerCommon.TAG) { cur.infoShort }

        yield(cur)

        cur.node.children().reversed().forEach { child ->
            queue.addFirst(CrawledNode(child, cur.level + 1))
        }
    }
}

// Recursive
fun AccessibilityNodeInfo.scrollNode(): Boolean {
    if (!isScrollable) return false

    log(CrawlerCommon.TAG, VERBOSE) { "Scrolling node: ${toStringShort()}" }
    return performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
}

val AccessibilityEvent.pkgId: Pkg.Id? get() = packageName.takeIf { !it.isNullOrBlank() }?.toString()?.toPkgId()