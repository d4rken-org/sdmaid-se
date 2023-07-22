package eu.darken.sdmse.systemcleaner.core.filter

import android.content.Context
import androidx.core.content.ContextCompat
import eu.darken.sdmse.R
import eu.darken.sdmse.systemcleaner.core.filter.generic.AdvertisementFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.AnalyticsFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.EmptyDirectoryFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.LinuxFilesFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.LogFilesFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.LostDirFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.MacFilesFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.SuperfluousApksFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.TempFilesFilter
import eu.darken.sdmse.systemcleaner.core.filter.generic.WindowsFilesFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.AnrFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.DataLocalTmpFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.DataLoggerFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.DownloadCacheFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.LogDropboxFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.RecentTasksFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.TombstonesFilter
import eu.darken.sdmse.systemcleaner.core.filter.specific.UsagestatsFilter

typealias FilterIdentifier = String

fun FilterIdentifier.getLabel(context: Context) = when (this) {
    LogFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_logfiles_label
    AdvertisementFilter::class.filterIdentifier -> R.string.systemcleaner_filter_advertisements_label
    EmptyDirectoryFilter::class.filterIdentifier -> R.string.systemcleaner_filter_emptydirectories_label
    SuperfluousApksFilter::class.filterIdentifier -> R.string.systemcleaner_filter_superfluosapks_label
    LostDirFilter::class.filterIdentifier -> R.string.systemcleaner_filter_lostdir_label
    LinuxFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_linuxfiles_label
    MacFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_macfiles_label
    WindowsFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_windowsfiles_label
    TempFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_tempfiles_label
    AnalyticsFilter::class.filterIdentifier -> R.string.systemcleaner_filter_analytics_label
    AnrFilter::class.filterIdentifier -> R.string.systemcleaner_filter_anr_label
    DataLocalTmpFilter::class.filterIdentifier -> R.string.systemcleaner_filter_localtmp_label
    DownloadCacheFilter::class.filterIdentifier -> R.string.systemcleaner_filter_downloadcache_label
    DataLoggerFilter::class.filterIdentifier -> R.string.systemcleaner_filter_datalogger_label
    LogDropboxFilter::class.filterIdentifier -> R.string.systemcleaner_filter_logdropbox_label
    RecentTasksFilter::class.filterIdentifier -> R.string.systemcleaner_filter_recenttasks_label
    TombstonesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_tombstones_label
    UsagestatsFilter::class.filterIdentifier -> R.string.systemcleaner_filter_usagestats_label
    else -> null
}?.let { context.getString(it) } ?: this

fun FilterIdentifier.getDescription(context: Context) = when (this) {
    LogFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_logfiles_summary
    AdvertisementFilter::class.filterIdentifier -> R.string.systemcleaner_filter_advertisements_summary
    EmptyDirectoryFilter::class.filterIdentifier -> R.string.systemcleaner_filter_emptydirectories_label
    SuperfluousApksFilter::class.filterIdentifier -> R.string.systemcleaner_filter_emptydirectories_summary
    LostDirFilter::class.filterIdentifier -> R.string.systemcleaner_filter_lostdir_summary
    LinuxFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_linuxfiles_summary
    MacFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_macfiles_summary
    WindowsFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_windowsfiles_summary
    TempFilesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_tempfiles_summary
    AnalyticsFilter::class.filterIdentifier -> R.string.systemcleaner_filter_analytics_summary
    AnrFilter::class.filterIdentifier -> R.string.systemcleaner_filter_anr_summary
    DataLocalTmpFilter::class.filterIdentifier -> R.string.systemcleaner_filter_localtmp_summary
    DownloadCacheFilter::class.filterIdentifier -> R.string.systemcleaner_filter_downloadcache_summary
    DataLoggerFilter::class.filterIdentifier -> R.string.systemcleaner_filter_datalogger_summary
    LogDropboxFilter::class.filterIdentifier -> R.string.systemcleaner_filter_logdropbox_summary
    RecentTasksFilter::class.filterIdentifier -> R.string.systemcleaner_filter_recenttasks_summary
    TombstonesFilter::class.filterIdentifier -> R.string.systemcleaner_filter_tombstones_summary
    UsagestatsFilter::class.filterIdentifier -> R.string.systemcleaner_filter_usagestats_summary
    else -> null
}?.let { context.getString(it) } ?: this

fun FilterIdentifier.getIcon(context: Context) = when (this) {
    LogFilesFilter::class.filterIdentifier -> R.drawable.ic_baseline_format_list_bulleted_24
    AdvertisementFilter::class.filterIdentifier -> R.drawable.ic_baseline_ads_click_24
    EmptyDirectoryFilter::class.filterIdentifier -> R.drawable.ic_baseline_folder_open_24
    SuperfluousApksFilter::class.filterIdentifier -> R.drawable.ic_app_extra_24
    LostDirFilter::class.filterIdentifier -> R.drawable.ic_baseline_usb_24
    LinuxFilesFilter::class.filterIdentifier -> R.drawable.ic_os_linux
    MacFilesFilter::class.filterIdentifier -> R.drawable.ic_os_mac
    WindowsFilesFilter::class.filterIdentifier -> R.drawable.ic_os_windows
    TempFilesFilter::class.filterIdentifier -> R.drawable.ic_baseline_access_time_filled_24
    AnalyticsFilter::class.filterIdentifier -> R.drawable.ic_analytics_onsurface
    AnrFilter::class.filterIdentifier -> R.drawable.ic_baseline_running_with_errors_24
    DataLocalTmpFilter::class.filterIdentifier -> R.drawable.ic_android_studio_24
    DownloadCacheFilter::class.filterIdentifier -> R.drawable.ic_android_studio_24
    DataLoggerFilter::class.filterIdentifier -> R.drawable.ic_baseline_format_list_bulleted_24
    LogDropboxFilter::class.filterIdentifier -> R.drawable.ic_baseline_format_list_bulleted_24
    RecentTasksFilter::class.filterIdentifier -> R.drawable.ic_task_onsurface
    TombstonesFilter::class.filterIdentifier -> R.drawable.ic_tombstone
    UsagestatsFilter::class.filterIdentifier -> R.drawable.ic_chart_bar_stacked_24
    else -> R.drawable.air_filter
}.let { ContextCompat.getDrawable(context, it) }