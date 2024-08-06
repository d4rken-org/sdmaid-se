package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardErrorDataareaItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class ErrorDataAreaVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<ErrorDataAreaVH.Item, DashboardErrorDataareaItemBinding>(
        R.layout.dashboard_error_dataarea_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardErrorDataareaItemBinding.bind(itemView) }

    override val onBindData: DashboardErrorDataareaItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        checkAction.setOnClickListener { item.onReload() }
    }

    data class Item(
        val state: DataAreaManager.State,
        val onReload: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}