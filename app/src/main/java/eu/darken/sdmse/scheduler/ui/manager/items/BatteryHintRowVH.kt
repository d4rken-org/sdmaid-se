package eu.darken.sdmse.scheduler.ui.manager.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SchedulerManagerListBatteryhintItemBinding
import eu.darken.sdmse.scheduler.ui.manager.SchedulerAdapter


class BatteryHintRowVH(parent: ViewGroup) :
    SchedulerAdapter.BaseVH<BatteryHintRowVH.Item, SchedulerManagerListBatteryhintItemBinding>(
        R.layout.scheduler_manager_list_batteryhint_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerManagerListBatteryhintItemBinding.bind(itemView) }

    override val onBindData: SchedulerManagerListBatteryhintItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        fixAction.setOnClickListener { item.onFix() }
        dismissAction.setOnClickListener { item.onDismiss() }
    }

    data class Item(
        val onFix: () -> Unit,
        val onDismiss: () -> Unit,
    ) : SchedulerAdapter.Item {

        override val stableId: Long = Item::class.java.hashCode().toLong()
    }
}