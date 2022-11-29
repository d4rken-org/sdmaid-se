package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardDebugItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class DebugCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DebugCardVH.Item, DashboardDebugItemBinding>(R.layout.dashboard_debug_item, parent) {

    override val viewBinding = lazy { DashboardDebugItemBinding.bind(itemView) }

    override val onBindData: DashboardDebugItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        checkAction.setOnClickListener { item.onCheck() }
    }

    data class Item(
        val onCheck: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}