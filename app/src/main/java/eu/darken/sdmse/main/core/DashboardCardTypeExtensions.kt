package eu.darken.sdmse.main.core

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Swipe
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
        DashboardCardType.SYSTEMCLEANER -> Icons.AutoMirrored.Outlined.ViewList
        DashboardCardType.APPCLEANER -> Icons.Outlined.Recycling
        DashboardCardType.DEDUPLICATOR -> Icons.Outlined.ContentCopy
        DashboardCardType.APPCONTROL -> Icons.Outlined.Apps
        DashboardCardType.ANALYZER -> Icons.Outlined.DataUsage
        DashboardCardType.SWIPER -> Icons.Outlined.Swipe
        DashboardCardType.SQUEEZER -> Icons.Outlined.Compress
        DashboardCardType.SCHEDULER -> Icons.Outlined.Alarm
        DashboardCardType.STATS -> Icons.Outlined.BarChart
    }
