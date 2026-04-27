package eu.darken.sdmse.stats.ui.spacehistory

import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.common.theming.SdmSeTheme
import eu.darken.sdmse.stats.core.db.ReportEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object SpaceHistoryMarkerTooltip {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun show(chart: SpaceHistoryChartView, report: ReportEntity, screenX: Int, screenY: Int) {
        val context = chart.context

        val time = report.endAt.atZone(ZoneId.systemDefault()).format(timeFormatter)
        val affectedSpace = report.affectedSpace
        val detailText = if (affectedSpace != null && affectedSpace > 0) {
            val freed = ByteFormatter.formatSize(context, affectedSpace).first
            "${context.getString(R.string.stats_space_history_marker_freed, freed)} · $time"
        } else {
            time
        }

        val composeView = ComposeView(context).apply {
            chart.findViewTreeLifecycleOwner()?.let(::setViewTreeLifecycleOwner)
            chart.findViewTreeViewModelStoreOwner()?.let(::setViewTreeViewModelStoreOwner)
            chart.findViewTreeSavedStateRegistryOwner()?.let(::setViewTreeSavedStateRegistryOwner)
            chart.findViewTreeCompositionContext()?.let(::setParentCompositionContext)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                SdmSeTheme {
                    SpaceHistoryTooltipContent(tool = report.tool, detailText = detailText)
                }
            }
        }

        val popup = PopupWindow(
            composeView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = 8f
            setOnDismissListener { chart.clearSelection() }
        }

        val margin = (12 * context.resources.displayMetrics.density).toInt()

        composeView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val w = composeView.measuredWidth
                val h = composeView.measuredHeight
                if (w == 0 || h == 0) return true
                composeView.viewTreeObserver.removeOnPreDrawListener(this)
                popup.update(screenX - w / 2, screenY - h - margin, w, h)
                return false
            }
        })

        popup.showAtLocation(chart, Gravity.NO_GRAVITY, screenX, screenY)
    }
}
