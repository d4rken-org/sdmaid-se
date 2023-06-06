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
    LogFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_logfiles_label
    AdvertisementFilter::class.qualifiedName -> R.string.systemcleaner_filter_advertisements_label
    EmptyDirectoryFilter::class.qualifiedName -> R.string.systemcleaner_filter_emptydirectories_label
    SuperfluousApksFilter::class.qualifiedName -> R.string.systemcleaner_filter_superfluosapks_label
    LostDirFilter::class.qualifiedName -> R.string.systemcleaner_filter_lostdir_label
    LinuxFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_linuxfiles_label
    MacFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_macfiles_label
    WindowsFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_windowsfiles_label
    TempFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_tempfiles_label
    AnalyticsFilter::class.qualifiedName -> R.string.systemcleaner_filter_analytics_label
    AnrFilter::class.qualifiedName -> R.string.systemcleaner_filter_anr_label
    DataLocalTmpFilter::class.qualifiedName -> R.string.systemcleaner_filter_localtmp_label
    DownloadCacheFilter::class.qualifiedName -> R.string.systemcleaner_filter_downloadcache_label
    DataLoggerFilter::class.qualifiedName -> R.string.systemcleaner_filter_datalogger_label
    LogDropboxFilter::class.qualifiedName -> R.string.systemcleaner_filter_logdropbox_label
    RecentTasksFilter::class.qualifiedName -> R.string.systemcleaner_filter_recenttasks_label
    TombstonesFilter::class.qualifiedName -> R.string.systemcleaner_filter_tombstones_label
    UsagestatsFilter::class.qualifiedName -> R.string.systemcleaner_filter_usagestats_label
    else -> null
}?.let { context.getString(it) } ?: this

fun FilterIdentifier.getDescription(context: Context) = when (this) {
    LogFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_logfiles_summary
    AdvertisementFilter::class.qualifiedName -> R.string.systemcleaner_filter_advertisements_summary
    EmptyDirectoryFilter::class.qualifiedName -> R.string.systemcleaner_filter_emptydirectories_label
    SuperfluousApksFilter::class.qualifiedName -> R.string.systemcleaner_filter_emptydirectories_summary
    LostDirFilter::class.qualifiedName -> R.string.systemcleaner_filter_lostdir_summary
    LinuxFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_linuxfiles_summary
    MacFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_macfiles_summary
    WindowsFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_windowsfiles_summary
    TempFilesFilter::class.qualifiedName -> R.string.systemcleaner_filter_tempfiles_summary
    AnalyticsFilter::class.qualifiedName -> R.string.systemcleaner_filter_analytics_summary
    AnrFilter::class.qualifiedName -> R.string.systemcleaner_filter_anr_summary
    DataLocalTmpFilter::class.qualifiedName -> R.string.systemcleaner_filter_localtmp_summary
    DownloadCacheFilter::class.qualifiedName -> R.string.systemcleaner_filter_downloadcache_summary
    DataLoggerFilter::class.qualifiedName -> R.string.systemcleaner_filter_datalogger_summary
    LogDropboxFilter::class.qualifiedName -> R.string.systemcleaner_filter_logdropbox_summary
    RecentTasksFilter::class.qualifiedName -> R.string.systemcleaner_filter_recenttasks_summary
    TombstonesFilter::class.qualifiedName -> R.string.systemcleaner_filter_tombstones_summary
    UsagestatsFilter::class.qualifiedName -> R.string.systemcleaner_filter_usagestats_summary
    else -> null
}?.let { context.getString(it) } ?: this

fun FilterIdentifier.getIcon(context: Context) = when (this) {
    LogFilesFilter::class.qualifiedName -> R.drawable.ic_baseline_format_list_bulleted_24
    AdvertisementFilter::class.qualifiedName -> R.drawable.ic_baseline_ads_click_24
    EmptyDirectoryFilter::class.qualifiedName -> R.drawable.ic_baseline_folder_open_24
    SuperfluousApksFilter::class.qualifiedName -> R.drawable.ic_app_extra_24
    LostDirFilter::class.qualifiedName -> R.drawable.ic_baseline_usb_24
    LinuxFilesFilter::class.qualifiedName -> R.drawable.ic_os_linux
    MacFilesFilter::class.qualifiedName -> R.drawable.ic_os_mac
    WindowsFilesFilter::class.qualifiedName -> R.drawable.ic_os_windows
    TempFilesFilter::class.qualifiedName -> R.drawable.ic_baseline_access_time_filled_24
    AnalyticsFilter::class.qualifiedName -> R.drawable.ic_analytics_onsurface
    AnrFilter::class.qualifiedName -> R.drawable.ic_baseline_running_with_errors_24
    DataLocalTmpFilter::class.qualifiedName -> R.drawable.ic_android_studio_24
    DownloadCacheFilter::class.qualifiedName -> R.drawable.ic_android_studio_24
    DataLoggerFilter::class.qualifiedName -> R.drawable.ic_baseline_format_list_bulleted_24
    LogDropboxFilter::class.qualifiedName -> R.drawable.ic_baseline_format_list_bulleted_24
    RecentTasksFilter::class.qualifiedName -> R.drawable.ic_task_onsurface
    TombstonesFilter::class.qualifiedName -> R.drawable.ic_tombstone
    UsagestatsFilter::class.qualifiedName -> R.drawable.ic_chart_bar_stacked_24
    else -> R.drawable.air_filter
}.let { ContextCompat.getDrawable(context, it) }