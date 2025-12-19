package eu.darken.sdmse.automation.core.common

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.pow
import kotlin.math.sqrt

private val TAG: String = logTag("Automation", "Crawler", "Common")

val ACSNodeInfo.textVariants: Set<String>
    get() {
        val target = text?.toString() ?: return emptySet()

        return setOf(
            target,
            target.replace(' ', ' ')
        )
    }

fun ACSNodeInfo.textMatchesAny(candidates: Collection<String>): Boolean =
    candidates.any { textMatches(it) }

fun ACSNodeInfo.textMatches(candidate: String): Boolean {
    return textVariants.any { it.equals(candidate, ignoreCase = true) }
}

fun ACSNodeInfo.textContainsAny(candidates: Collection<String>): Boolean =
    candidates.any { candidate -> textVariants.any { it.contains(candidate, ignoreCase = true) } }

fun ACSNodeInfo.textEndsWithAny(candidates: Collection<String>): Boolean =
    candidates.any { candidate -> textVariants.any { it.endsWith(candidate, ignoreCase = true) } }

val ACSNodeInfo.contentDescVariants: Set<String>
    get() {
        val target = contentDescription?.toString() ?: return emptySet()
        return setOf(target, target.replace(' ', ' '))
    }

fun ACSNodeInfo.contentDescMatches(candidate: String): Boolean {
    return contentDescVariants.any { it.equals(candidate, ignoreCase = true) }
}

fun ACSNodeInfo.contentDescMatchesAny(candidates: Collection<String>): Boolean =
    candidates.any { contentDescMatches(it) }

fun ACSNodeInfo.idMatches(id: String): Boolean {
    return viewIdResourceName == id
}

fun ACSNodeInfo.idContains(id: String): Boolean {
    return viewIdResourceName?.contains(id) == true
}

fun ACSNodeInfo.isClickyButton(): Boolean {
    return isClickable && className == "android.widget.Button"
}

fun ACSNodeInfo.isTextView(): Boolean {
    return className == "android.widget.TextView"
}

fun ACSNodeInfo.isRadioButton(): Boolean {
    return className == "android.widget.RadioButton"
}

fun ACSNodeInfo.children() = (0 until childCount).mapNotNull { getChild(it) }

fun ACSNodeInfo.findParentOrNull(
    maxNesting: Int = 3,
    predicate: (ACSNodeInfo) -> Boolean
): ACSNodeInfo? {
    var target = this.parent ?: return null
    for (i in 1..maxNesting) {
        if (predicate(target)) return target
        target = target.parent ?: break
    }
    return null
}

fun ACSNodeInfo.getRoot(maxNesting: Int = 15 /*AOSP*/): ACSNodeInfo {
    var target: ACSNodeInfo = this
    for (i in 1..maxNesting) {
        target.parent?.let {
            target = it
        } ?: break
    }
    return target
}

fun ACSNodeInfo.crawl(debug: Boolean = Bugs.isTrace): Sequence<CrawledNode> = sequence {
    try {
        if (this@crawl.getChild(0) == null) {
            this@crawl.refresh().let { log(TAG) { "Refresh success: $it" } }
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "Null crawl failed to refresh: ${e.asLog()}" }
    }

    val queue = LinkedBlockingDeque<CrawledNode>()
    queue.add(CrawledNode(this@crawl, 0))

    while (!queue.isEmpty()) {
        val cur = queue.poll()!!

        if (debug) log(TAG, VERBOSE) { cur.infoShort }

        yield(cur)

        cur.node.children().reversed().forEach { child ->
            queue.addFirst(CrawledNode(child, cur.level + 1))
        }
    }
}

// Recursive
fun ACSNodeInfo.scrollNode(): Boolean {
    if (!isScrollable) {
        log(TAG, WARN) { "scrollNode(): Not scrollable: $this" }
        return false
    }

    log(TAG, VERBOSE) { "scrollNode(): Scrolling node: $this" }
    return performAction(ACSNodeInfo.ACTION_SCROLL_FORWARD).also {
        log(TAG, VERBOSE) { "scrollNode(): Successfully scrolled: $this" }
    }
}

val AccessibilityEvent.pkgId: Pkg.Id? get() = packageName.takeIf { !it.isNullOrBlank() }?.toString()?.toPkgId()

fun ACSNodeInfo.distanceTo(other: ACSNodeInfo): Double {
    val rect1 = Rect().apply { this@distanceTo.getBoundsInScreen(this) }
    val rect2 = Rect().apply { other.getBoundsInScreen(this) }

    val x1 = rect1.centerX().toDouble()
    val y1 = rect1.centerY().toDouble()
    val x2 = rect2.centerX().toDouble()
    val y2 = rect2.centerY().toDouble()

    return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
}