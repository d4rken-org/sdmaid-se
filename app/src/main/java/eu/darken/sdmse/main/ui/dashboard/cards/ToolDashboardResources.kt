package eu.darken.sdmse.main.ui.dashboard.cards

import eu.darken.sdmse.appcleaner.R as AppCleanerR
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.corpsefinder.R as CorpseFinderR
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

internal fun toolNameRes(type: SDMTool.Type): Int = when (type) {
    SDMTool.Type.CORPSEFINDER -> CommonR.string.corpsefinder_tool_name
    SDMTool.Type.SYSTEMCLEANER -> CommonR.string.systemcleaner_tool_name
    SDMTool.Type.APPCLEANER -> CommonR.string.appcleaner_tool_name
    SDMTool.Type.DEDUPLICATOR -> CommonR.string.deduplicator_tool_name
    else -> error("Unsupported tool type: $type")
}

internal fun toolDescriptionRes(type: SDMTool.Type): Int = when (type) {
    SDMTool.Type.CORPSEFINDER -> CorpseFinderR.string.corpsefinder_explanation_short
    SDMTool.Type.SYSTEMCLEANER -> SystemCleanerR.string.systemcleaner_explanation_short
    SDMTool.Type.APPCLEANER -> AppCleanerR.string.appcleaner_explanation_short
    SDMTool.Type.DEDUPLICATOR -> DeduplicatorR.string.deduplicator_explanation_short
    else -> error("Unsupported tool type: $type")
}
