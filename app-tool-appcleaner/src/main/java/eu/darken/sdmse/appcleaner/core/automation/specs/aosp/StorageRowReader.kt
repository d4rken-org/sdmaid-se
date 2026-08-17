package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.children

internal const val ROW_TITLE_ID = "android:id/title"
internal const val ROW_SUMMARY_ID = "android:id/summary"

/**
 * Reads the value of the storage row this title belongs to.
 *
 * Rows are usually `container(title, summary)`, but some ROMs wrap the title one level deeper (see
 * StorageEntryFinderTest), so walk up until a container holds the summary. Only direct children are
 * considered at each level, so a container holding several rows can't leak a neighbour's value: our
 * own summary sits deeper and would have matched first.
 */
internal fun ACSNodeInfo.findRowSummaryText(maxAscend: Int = 2): String? {
    var current: ACSNodeInfo? = parent
    repeat(maxAscend) {
        val container = current ?: return null
        container.children()
            .firstOrNull { it.viewIdResourceName == ROW_SUMMARY_ID }
            ?.text?.toString()
            ?.let { return it }
        current = container.parent
    }
    return null
}
