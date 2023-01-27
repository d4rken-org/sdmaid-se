package eu.darken.sdmse.appcontrol.ui

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.AppcontrolDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class AppControlDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<AppControlDashCardVH.Item, AppcontrolDashboardItemBinding>(
        R.layout.appcontrol_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolDashboardItemBinding.bind(itemView) }

    override val onBindData: AppcontrolDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        root.setOnClickListener { viewAction.performClick() }
        viewAction.apply {
            setOnClickListener { item.onViewDetails() }
        }
    }

    data class Item(
        val data: AppControl.Data?,
        val progress: Progress.Data?,
        val onViewDetails: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}