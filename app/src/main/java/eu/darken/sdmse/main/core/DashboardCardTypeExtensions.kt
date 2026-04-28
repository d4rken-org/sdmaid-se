package eu.darken.sdmse.main.core

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.BarChart
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.Recycling
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Ghost
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

val DashboardCardType.icon: ImageVector
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> SdmIcons.Ghost
        DashboardCardType.SYSTEMCLEANER -> Icons.AutoMirrored.TwoTone.ViewList
        DashboardCardType.APPCLEANER -> Icons.TwoTone.Recycling
        DashboardCardType.DEDUPLICATOR -> Icons.TwoTone.ContentCopy
        DashboardCardType.APPCONTROL -> Icons.TwoTone.Apps
        DashboardCardType.ANALYZER -> Icons.TwoTone.DataUsage
        DashboardCardType.SWIPER -> Icons.TwoTone.Swipe
        DashboardCardType.SQUEEZER -> Icons.TwoTone.Compress
        DashboardCardType.SCHEDULER -> Icons.TwoTone.Alarm
        DashboardCardType.STATS -> Icons.TwoTone.BarChart
    }
