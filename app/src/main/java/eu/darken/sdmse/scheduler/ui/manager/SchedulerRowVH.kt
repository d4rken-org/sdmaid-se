package eu.darken.sdmse.scheduler.ui.manager

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SchedulerManagerListItemBinding
import eu.darken.sdmse.scheduler.core.Schedule


class SchedulerRowVH(parent: ViewGroup) :
    SchedulerAdapter.BaseVH<SchedulerRowVH.Item, SchedulerManagerListItemBinding>(
        R.layout.scheduler_manager_list_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerManagerListItemBinding.bind(itemView) }

    override val onBindData: SchedulerManagerListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        primary.text = item.schedule.label
        secondary.text = item.schedule.id

        root.setOnClickListener { item.onItemClick() }
    }

    data class Item(
        val schedule: Schedule,
        val onItemClick: () -> Unit,
    ) : SchedulerAdapter.Item {

        override val stableId: Long = schedule.id.hashCode().toLong()
    }

}