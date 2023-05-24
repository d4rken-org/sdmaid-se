package eu.darken.sdmse.main.core

import androidx.annotation.StringRes
import eu.darken.sdmse.R

@get:StringRes
val SDMTool.Type.labelRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.string.corpsefinder_tool_name
        SDMTool.Type.SYSTEMCLEANER -> R.string.systemcleaner_tool_name
        SDMTool.Type.APPCLEANER -> R.string.appcleaner_tool_name
        SDMTool.Type.APPCONTROL -> R.string.appcontrol_tool_name
        SDMTool.Type.ANALYZER -> R.string.analyzer_tool_name
    }