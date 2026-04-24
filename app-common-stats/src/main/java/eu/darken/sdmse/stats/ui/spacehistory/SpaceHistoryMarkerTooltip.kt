package eu.darken.sdmse.stats.ui.spacehistory

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.main.core.iconRes
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.stats.core.db.ReportEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object SpaceHistoryMarkerTooltip {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun show(chart: SpaceHistoryChartView, report: ReportEntity, screenX: Int, screenY: Int) {
        val context = chart.context
        val tooltipView = LayoutInflater.from(context)
            .inflate(R.layout.stats_space_history_marker_tooltip, null)
        tooltipView.findViewById<ImageView>(R.id.tooltip_icon).setImageResource(report.tool.iconRes)
        tooltipView.findViewById<TextView>(R.id.tooltip_tool_name).setText(report.tool.labelRes)

        val detail = tooltipView.findViewById<TextView>(R.id.tooltip_detail)
        val time = report.endAt.atZone(ZoneId.systemDefault()).format(timeFormatter)
        detail.text = if (report.affectedSpace != null && report.affectedSpace!! > 0) {
            val freed = ByteFormatter.formatSize(context, report.affectedSpace!!).first
            "${context.getString(R.string.stats_space_history_marker_freed, freed)} · $time"
        } else {
            time
        }

        val popup = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = 8f
            setOnDismissListener { chart.clearSelection() }
        }

        tooltipView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val margin = (12 * context.resources.displayMetrics.density).toInt()
        val popupX = screenX - tooltipView.measuredWidth / 2
        val popupY = screenY - tooltipView.measuredHeight - margin

        popup.showAtLocation(chart, Gravity.NO_GRAVITY, popupX, popupY)
    }
}
