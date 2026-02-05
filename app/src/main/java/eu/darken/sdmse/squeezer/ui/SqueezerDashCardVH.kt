package eu.darken.sdmse.squeezer.ui

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.databinding.SqueezerDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class SqueezerDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<SqueezerDashCardVH.Item, SqueezerDashboardItemBinding>(
        R.layout.squeezer_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { SqueezerDashboardItemBinding.bind(itemView) }

    override val onBindData: SqueezerDashboardItemBinding.(
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
        val data: Squeezer.Data?,
        val isInitializing: Boolean,
        val isNew: Boolean,
        val progress: Progress.Data?,
        val onViewDetails: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}
