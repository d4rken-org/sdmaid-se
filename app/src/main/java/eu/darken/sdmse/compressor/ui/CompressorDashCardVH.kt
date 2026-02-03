package eu.darken.sdmse.compressor.ui

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.compressor.core.Compressor
import eu.darken.sdmse.databinding.CompressorDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class CompressorDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<CompressorDashCardVH.Item, CompressorDashboardItemBinding>(
        R.layout.compressor_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { CompressorDashboardItemBinding.bind(itemView) }

    override val onBindData: CompressorDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        newBadge.root.isGone = !item.isNew
        toolLoadingIndicator.isGone = !item.isInitializing
        viewAction.apply {
            isEnabled = !item.isInitializing
            setOnClickListener { item.onViewDetails() }
        }
        root.apply {
            setOnClickListener { viewAction.performClick() }
            isClickable = !item.isInitializing
        }
    }

    data class Item(
        val data: Compressor.Data?,
        val isInitializing: Boolean,
        val isNew: Boolean,
        val progress: Progress.Data?,
        val onViewDetails: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}
