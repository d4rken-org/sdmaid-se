package eu.darken.sdmse.scheduler.ui.manager.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SchedulerManagerListAlarmhintItemBinding
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.ui.manager.SchedulerAdapter


class AlarmHintRowVH(parent: ViewGroup) :
    SchedulerAdapter.BaseVH<AlarmHintRowVH.Item, SchedulerManagerListAlarmhintItemBinding>(
        R.layout.scheduler_manager_list_alarmhint_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerManagerListAlarmhintItemBinding.bind(itemView) }

    override val onBindData: SchedulerManagerListAlarmhintItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

    }

    data class Item(
        val state: SchedulerManager.State,
    ) : SchedulerAdapter.Item {

        override val stableId: Long = Item::class.java.hashCode().toLong()
    }
}