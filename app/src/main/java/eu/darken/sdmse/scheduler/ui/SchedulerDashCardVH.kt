package eu.darken.sdmse.scheduler.ui

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SchedulerDashboardItemBinding
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.scheduler.core.SchedulerManager


class SchedulerDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<SchedulerDashCardVH.Item, SchedulerDashboardItemBinding>(
        R.layout.scheduler_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerDashboardItemBinding.bind(itemView) }

    override val onBindData: SchedulerDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        root.setOnClickListener { manageAction.performClick() }
        manageAction.apply {
            setOnClickListener { item.onManageClicked() }
        }
    }

    data class Item(
        val schedulerState: SchedulerManager.State,
        val taskState: TaskManager.State,
        val onManageClicked: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}