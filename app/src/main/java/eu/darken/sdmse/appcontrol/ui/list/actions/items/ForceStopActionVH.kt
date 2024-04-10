package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionForcestopItemBinding


class ForceStopActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<ForceStopActionVH.Item, AppcontrolActionForcestopItemBinding>(
        R.layout.appcontrol_action_forcestop_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionForcestopItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionForcestopItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        itemView.setOnClickListener { item.onForceStop(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onForceStop: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}