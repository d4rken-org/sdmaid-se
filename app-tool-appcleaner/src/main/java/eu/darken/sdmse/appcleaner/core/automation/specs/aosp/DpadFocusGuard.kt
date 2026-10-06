package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo

/**
 * Whether this focused node is demonstrably outside the storage page's button row, i.e. a node of
 * another package or one entirely above [anchor] (the app bar's up and search buttons). The anchor
 * itself, nodes overlapping or below it, and unusable bounds on either node are inconclusive: `false`.
 *
 * ```
 * Button "Search settings"       [948,120 - 1080,264] <- focused
 * entity_header_content (anchor) [42,300 - 1038,702]
 * ```
 * Focused bottom 264 <= anchor top 300: `true`.
 */
internal fun ACSNodeInfo.isOutsideButtonRow(anchor: ACSNodeInfo): Boolean {
    val pkg = packageName?.toString()
    if (pkg != null && pkg != AOSPSpecs.SETTINGS_PKG.name) return true

    if (viewIdResourceName == ENTITY_HEADER_CONTENT_ID) return false

    val focused = getScreenBounds()
    val header = anchor.getScreenBounds()
    if (!focused.isUsable() || !header.isUsable()) return false

    return focused.bottom <= header.top
}

private fun ACSNodeInfo.ScreenBounds.isUsable(): Boolean = right > left && bottom > top
