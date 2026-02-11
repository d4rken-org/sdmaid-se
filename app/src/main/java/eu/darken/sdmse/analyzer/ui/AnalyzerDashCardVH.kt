package eu.darken.sdmse.analyzer.ui

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.AnalyzerDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import kotlin.math.absoluteValue


class AnalyzerDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<AnalyzerDashCardVH.Item, AnalyzerDashboardItemBinding>(
        R.layout.analyzer_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerDashboardItemBinding.bind(itemView) }

    override val onBindData: AnalyzerDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        val combinedDelta = item.combinedDelta

        trendDelta.isVisible = combinedDelta != null
        if (combinedDelta != null) {
            val absDelta = Formatter.formatShortFileSize(context, combinedDelta.absoluteValue)
            val signedDelta = when {
                combinedDelta > 0 -> "+$absDelta"
                combinedDelta < 0 -> "-$absDelta"
                else -> absDelta
            }
            trendDelta.text = getString(R.string.analyzer_storage_trend_delta_in_7d, signedDelta)
            trendDelta.setTextColor(
                when {
                    combinedDelta > 0 -> getColorForAttr(android.R.attr.colorError)
                    combinedDelta < 0 -> getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
                    else -> getColorForAttr(android.R.attr.textColorSecondary)
                }
            )
        }

        viewAction.setOnClickListener { item.onViewDetails() }
        root.setOnClickListener { viewAction.performClick() }
    }

    data class Item(
        val data: Analyzer.Data?,
        val progress: Progress.Data?,
        val combinedDelta: Long? = null,
        val onViewDetails: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}
