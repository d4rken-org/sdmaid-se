package eu.darken.sdmse.main.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R

@get:StringRes
val SDMTool.Type.labelRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.string.corpsefinder_tool_name
        SDMTool.Type.SYSTEMCLEANER -> R.string.systemcleaner_tool_name
        SDMTool.Type.APPCLEANER -> R.string.appcleaner_tool_name
        SDMTool.Type.DEDUPLICATOR -> R.string.deduplicator_tool_name
        SDMTool.Type.APPCONTROL -> R.string.appcontrol_tool_name
        SDMTool.Type.ANALYZER -> R.string.analyzer_tool_name
    }


@get:DrawableRes
val SDMTool.Type.iconRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.drawable.ghost
        SDMTool.Type.SYSTEMCLEANER -> R.drawable.ic_baseline_view_list_24
        SDMTool.Type.APPCLEANER -> R.drawable.ic_recycle
        SDMTool.Type.DEDUPLICATOR -> R.drawable.ic_content_duplicate_24
        SDMTool.Type.APPCONTROL -> R.drawable.ic_apps
        SDMTool.Type.ANALYZER -> R.drawable.baseline_data_usage_24
    }