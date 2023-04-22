package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionAppstoreItemBinding


class AppStoreActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<AppStoreActionVH.Item, AppcontrolActionAppstoreItemBinding>(
        R.layout.appcontrol_action_appstore_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionAppstoreItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionAppstoreItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        itemView.setOnClickListener { item.onAppStore(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onAppStore: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}