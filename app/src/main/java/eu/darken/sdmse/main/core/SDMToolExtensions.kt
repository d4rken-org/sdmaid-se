package eu.darken.sdmse.main.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.common.R as CommonR

@get:StringRes
val DashboardCardType.labelRes: Int
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> CommonR.string.corpsefinder_tool_name
        DashboardCardType.SYSTEMCLEANER -> CommonR.string.systemcleaner_tool_name
        DashboardCardType.APPCLEANER -> CommonR.string.appcleaner_tool_name
        DashboardCardType.DEDUPLICATOR -> CommonR.string.deduplicator_tool_name
        DashboardCardType.APPCONTROL -> CommonR.string.appcontrol_tool_name
        DashboardCardType.ANALYZER -> CommonR.string.analyzer_tool_name
        DashboardCardType.SWIPER -> CommonR.string.swiper_tool_name
        DashboardCardType.SQUEEZER -> CommonR.string.squeezer_tool_name
        DashboardCardType.SCHEDULER -> eu.darken.sdmse.scheduler.R.string.scheduler_label
        DashboardCardType.STATS -> CommonR.string.stats_label
    }

@get:DrawableRes
val DashboardCardType.iconRes: Int
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> CommonR.drawable.ghost
        DashboardCardType.SYSTEMCLEANER -> CommonR.drawable.ic_baseline_view_list_24
        DashboardCardType.APPCLEANER -> CommonR.drawable.ic_recycle
        DashboardCardType.DEDUPLICATOR -> CommonR.drawable.ic_content_duplicate_24
        DashboardCardType.APPCONTROL -> CommonR.drawable.ic_apps
        DashboardCardType.ANALYZER -> CommonR.drawable.baseline_data_usage_24
        DashboardCardType.SWIPER -> CommonR.drawable.ic_baseline_swipe_24
        DashboardCardType.SQUEEZER -> CommonR.drawable.ic_image_compress_24
        DashboardCardType.SCHEDULER -> CommonR.drawable.ic_alarm_check_24
        DashboardCardType.STATS -> CommonR.drawable.ic_chartbox_24
    }
