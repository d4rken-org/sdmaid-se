package eu.darken.sdmse.automation.core.common

import android.view.accessibility.AccessibilityNodeInfo

data class CrawledNode(
    val node: AccessibilityNodeInfo,
    val level: Int
) {

    private val levelPrefix = "crawl():${INDENT.substring(0, level)}${level}"

    val infoFull: String = "$levelPrefix: $node"

    val infoShort: String = "$levelPrefix: ${node.toStringShort()}"

    companion object {
        const val INDENT =
            "--------------------------------------------------------------------------------------------------------------------"
    }
}