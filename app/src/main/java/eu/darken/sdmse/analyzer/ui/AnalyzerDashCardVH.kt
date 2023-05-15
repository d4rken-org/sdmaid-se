package eu.darken.sdmse.analyzer.ui

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.AnalyzerDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


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

        root.setOnClickListener { viewAction.performClick() }
        viewAction.apply {
            setOnClickListener { item.onViewDetails() }
        }
    }

    data class Item(
        val data: Analyzer.Data?,
        val progress: Progress.Data?,
        val onViewDetails: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}