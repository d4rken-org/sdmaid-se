package eu.darken.sdmse.main.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.R

@JsonClass(generateAdapter = false)
enum class DashboardCardType(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    CORPSEFINDER(R.string.corpsefinder_tool_name, R.drawable.ghost),
    SYSTEMCLEANER(R.string.systemcleaner_tool_name, R.drawable.ic_baseline_view_list_24),
    APPCLEANER(R.string.appcleaner_tool_name, R.drawable.ic_recycle),
    DEDUPLICATOR(R.string.deduplicator_tool_name, R.drawable.ic_content_duplicate_24),
    APPCONTROL(R.string.appcontrol_tool_name, R.drawable.ic_apps),
    ANALYZER(R.string.analyzer_tool_name, R.drawable.baseline_data_usage_24),
    COMPRESSOR(R.string.compressor_tool_name, R.drawable.ic_image_compress_24),
    SCHEDULER(R.string.scheduler_label, R.drawable.ic_alarm_check_24),
    STATS(R.string.stats_label, R.drawable.ic_chartbox_24),
}
