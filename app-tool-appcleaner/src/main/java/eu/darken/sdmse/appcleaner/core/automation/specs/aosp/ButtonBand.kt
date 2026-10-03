package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl

internal const val ENTITY_HEADER_CONTENT_ID = "com.android.settings:id/entity_header_content"

private const val MAX_LIST_ASCEND = 8

/**
 * Whether the strip between the app header item of Settings' storage list and the next list item is
 * at least [minGapPx] tall and no node in the window starts inside it, i.e. the button row is drawn
 * there but withheld from the accessibility tree. Called on the window root; fails closed.
 *
 * ```
 * RecyclerView
 *   FrameLayout  [0,234 - 1080,739]   <- header item, holds entity_header_content
 *   LinearLayout [0,1035 - 1080,1198] <- next item
 * ```
 * Strip 739..1035 (296px), no node top inside it: `true` for `minGapPx = 132`.
 */
internal fun ACSNodeInfo.hasEmptyButtonBand(minGapPx: Int): Boolean {
    if (minGapPx <= 0) return false

    val nodes = crawl().map { it.node }.toList()
    val allBounds = nodes.map { it.getScreenBounds() }

    val anchor = nodes.firstOrNull { it.viewIdResourceName == ENTITY_HEADER_CONTENT_ID } ?: return false
    val (list, headerItem) = anchor.findListAndItem() ?: return false
    val items = list.childrenIfComplete() ?: return false

    val header = headerItem.getScreenBounds()
    val next = items
        .filter { it != headerItem }
        .map { it.getScreenBounds() }
        .filter { it.top >= header.bottom && it.bottom > it.top }
        .minByOrNull { it.top }
        ?: return false

    if (next.top - header.bottom < minGapPx) return false

    return allBounds.none { it.top > header.bottom && it.top < next.top }
}

private fun ACSNodeInfo.findListAndItem(): Pair<ACSNodeInfo, ACSNodeInfo>? {
    var item = this
    repeat(MAX_LIST_ASCEND) {
        val parent = item.parent ?: return null
        if (parent.className?.toString()?.endsWith("RecyclerView") == true) return parent to item
        item = parent
    }
    return null
}

private fun ACSNodeInfo.childrenIfComplete(): List<ACSNodeInfo>? =
    (0 until childCount).map { getChild(it) ?: return null }
