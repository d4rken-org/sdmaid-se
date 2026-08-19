package eu.darken.sdmse.main.core

import androidx.annotation.DrawableRes
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
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.AppCleanerListRoute
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Ghost
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderListRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorListRoute
import eu.darken.sdmse.scheduler.ui.SchedulerManagerRoute
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.stats.ui.ReportsRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerListRoute
import eu.darken.sdmse.common.R as CommonR

@get:StringRes
val DashboardCardType.labelRes: Int
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> CommonR.string.corpsefinder_tool_name
        DashboardCardType.SYSTEMCLEANER -> CommonR.string.systemcleaner_tool_name
        DashboardCardType.APPCLEANER -> CommonR.string.appcleaner_tool_name
        DashboardCardType.DEDUPLICATOR -> CommonR.string.deduplicator_tool_name
        DashboardCardType.APPCONTROL -> CommonR.string.appcontrol_tool_name
        DashboardCardType.SWIPER -> CommonR.string.swiper_tool_name
        DashboardCardType.SQUEEZER -> CommonR.string.squeezer_tool_name
        DashboardCardType.ANALYZER -> CommonR.string.analyzer_tool_name
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
        DashboardCardType.SWIPER -> Icons.TwoTone.Swipe
        DashboardCardType.SQUEEZER -> Icons.TwoTone.Compress
        DashboardCardType.ANALYZER -> Icons.TwoTone.DataUsage
        DashboardCardType.SCHEDULER -> Icons.TwoTone.Alarm
        DashboardCardType.STATS -> Icons.TwoTone.BarChart
    }

/** Launcher-shortcut icon. Same glyph as [icon], but as a resource the framework can load. */
@get:DrawableRes
val DashboardCardType.shortcutIconRes: Int
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> R.drawable.ic_shortcut_corpsefinder
        DashboardCardType.SYSTEMCLEANER -> R.drawable.ic_shortcut_systemcleaner
        DashboardCardType.APPCLEANER -> R.drawable.ic_shortcut_appcleaner
        DashboardCardType.DEDUPLICATOR -> R.drawable.ic_shortcut_deduplicator
        DashboardCardType.APPCONTROL -> R.drawable.ic_shortcut_apps
        DashboardCardType.SWIPER -> R.drawable.ic_shortcut_swiper
        DashboardCardType.SQUEEZER -> R.drawable.ic_shortcut_squeezer
        DashboardCardType.ANALYZER -> R.drawable.ic_shortcut_analyzer
        DashboardCardType.SCHEDULER -> R.drawable.ic_shortcut_scheduler
        DashboardCardType.STATS -> R.drawable.ic_shortcut_stats
    }

/** Where a launcher shortcut for this tool takes the user. */
val DashboardCardType.shortcutRoute: NavigationDestination
    get() = when (this) {
        DashboardCardType.CORPSEFINDER -> CorpseFinderListRoute
        DashboardCardType.SYSTEMCLEANER -> SystemCleanerListRoute
        DashboardCardType.APPCLEANER -> AppCleanerListRoute
        DashboardCardType.DEDUPLICATOR -> DeduplicatorListRoute
        DashboardCardType.APPCONTROL -> AppControlListRoute
        DashboardCardType.SWIPER -> SwiperSessionsRoute
        // The dashboard's own Squeezer entry, which handles the setup gate.
        DashboardCardType.SQUEEZER -> SqueezerSetupRoute
        DashboardCardType.ANALYZER -> DeviceStorageRoute
        DashboardCardType.SCHEDULER -> SchedulerManagerRoute
        DashboardCardType.STATS -> ReportsRoute
    }
