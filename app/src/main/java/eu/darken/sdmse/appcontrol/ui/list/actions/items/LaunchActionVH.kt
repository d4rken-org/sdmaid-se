package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionLaunchItemBinding


class LaunchActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<LaunchActionVH.Item, AppcontrolActionLaunchItemBinding>(
        R.layout.appcontrol_action_launch_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionLaunchItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionLaunchItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        itemView.setOnClickListener { item.onLaunch(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onLaunch: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}