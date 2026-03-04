package eu.darken.sdmse.main.core

import androidx.annotation.DrawableRes
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

@get:DrawableRes
val SDMTool.Type.iconRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> CommonR.drawable.ghost
        SDMTool.Type.SYSTEMCLEANER -> CommonR.drawable.ic_baseline_view_list_24
        SDMTool.Type.APPCLEANER -> CommonR.drawable.ic_recycle
        SDMTool.Type.DEDUPLICATOR -> CommonR.drawable.ic_content_duplicate_24
        SDMTool.Type.SQUEEZER -> CommonR.drawable.ic_image_compress_24
        SDMTool.Type.APPCONTROL -> CommonR.drawable.ic_apps
        SDMTool.Type.ANALYZER -> CommonR.drawable.baseline_data_usage_24
        SDMTool.Type.SWIPER -> CommonR.drawable.ic_baseline_swipe_24
    }
