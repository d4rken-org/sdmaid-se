package eu.darken.sdmse.main.core

import androidx.annotation.StringRes
import eu.darken.sdmse.common.R as CommonR

@get:StringRes
val SDMTool.Type.labelRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> CommonR.string.corpsefinder_tool_name
        SDMTool.Type.SYSTEMCLEANER -> CommonR.string.systemcleaner_tool_name
        SDMTool.Type.APPCLEANER -> CommonR.string.appcleaner_tool_name
        SDMTool.Type.DEDUPLICATOR -> CommonR.string.deduplicator_tool_name
        SDMTool.Type.SQUEEZER -> CommonR.string.squeezer_tool_name
        SDMTool.Type.APPCONTROL -> CommonR.string.appcontrol_tool_name
        SDMTool.Type.ANALYZER -> CommonR.string.analyzer_tool_name
        SDMTool.Type.SWIPER -> CommonR.string.swiper_tool_name
    }
