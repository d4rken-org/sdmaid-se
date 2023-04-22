package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionUninstallItemBinding


class UninstallActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<UninstallActionVH.Item, AppcontrolActionUninstallItemBinding>(
        R.layout.appcontrol_action_uninstall_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionUninstallItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionUninstallItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        root.setOnClickListener { item.onItemClicked(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onItemClicked: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}