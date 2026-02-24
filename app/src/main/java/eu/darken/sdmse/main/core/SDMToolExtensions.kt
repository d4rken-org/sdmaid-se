package eu.darken.sdmse.main.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR

@get:StringRes
val SDMTool.Type.labelRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_tool_name
        SDMTool.Type.SYSTEMCLEANER -> eu.darken.sdmse.systemcleaner.R.string.systemcleaner_tool_name
        SDMTool.Type.APPCLEANER -> eu.darken.sdmse.appcleaner.R.string.appcleaner_tool_name
        SDMTool.Type.DEDUPLICATOR -> eu.darken.sdmse.deduplicator.R.string.deduplicator_tool_name
        SDMTool.Type.SQUEEZER -> eu.darken.sdmse.squeezer.R.string.squeezer_tool_name
        SDMTool.Type.APPCONTROL -> eu.darken.sdmse.appcontrol.R.string.appcontrol_tool_name
        SDMTool.Type.ANALYZER -> eu.darken.sdmse.analyzer.R.string.analyzer_tool_name
        SDMTool.Type.SWIPER -> eu.darken.sdmse.swiper.R.string.swiper_tool_name
    }


@get:DrawableRes
val SDMTool.Type.iconRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.drawable.ghost
        SDMTool.Type.SYSTEMCLEANER -> R.drawable.ic_baseline_view_list_24
        SDMTool.Type.APPCLEANER -> R.drawable.ic_recycle
        SDMTool.Type.DEDUPLICATOR -> R.drawable.ic_content_duplicate_24
        SDMTool.Type.SQUEEZER -> R.drawable.ic_image_compress_24
        SDMTool.Type.APPCONTROL -> CommonR.drawable.ic_apps
        SDMTool.Type.ANALYZER -> R.drawable.baseline_data_usage_24
        SDMTool.Type.SWIPER -> R.drawable.ic_baseline_swipe_24
    }

@get:StringRes
val DashboardCardType.labelRes: Int
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_tool_name
        DashboardCardType.SYSTEMCLEANER -> eu.darken.sdmse.systemcleaner.R.string.systemcleaner_tool_name
        DashboardCardType.APPCLEANER -> eu.darken.sdmse.appcleaner.R.string.appcleaner_tool_name
        DashboardCardType.DEDUPLICATOR -> eu.darken.sdmse.deduplicator.R.string.deduplicator_tool_name
        DashboardCardType.APPCONTROL -> eu.darken.sdmse.appcontrol.R.string.appcontrol_tool_name
        DashboardCardType.ANALYZER -> eu.darken.sdmse.analyzer.R.string.analyzer_tool_name
        DashboardCardType.SWIPER -> eu.darken.sdmse.swiper.R.string.swiper_tool_name
        DashboardCardType.SQUEEZER -> eu.darken.sdmse.squeezer.R.string.squeezer_tool_name
        DashboardCardType.SCHEDULER -> R.string.scheduler_label
        DashboardCardType.STATS -> R.string.stats_label
    }

@get:DrawableRes
val DashboardCardType.iconRes: Int
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> R.drawable.ghost
        DashboardCardType.SYSTEMCLEANER -> R.drawable.ic_baseline_view_list_24
        DashboardCardType.APPCLEANER -> R.drawable.ic_recycle
        DashboardCardType.DEDUPLICATOR -> R.drawable.ic_content_duplicate_24
        DashboardCardType.APPCONTROL -> CommonR.drawable.ic_apps
        DashboardCardType.ANALYZER -> R.drawable.baseline_data_usage_24
        DashboardCardType.SWIPER -> R.drawable.ic_baseline_swipe_24
        DashboardCardType.SQUEEZER -> R.drawable.ic_image_compress_24
        DashboardCardType.SCHEDULER -> R.drawable.ic_alarm_check_24
        DashboardCardType.STATS -> R.drawable.ic_chartbox_24
    }