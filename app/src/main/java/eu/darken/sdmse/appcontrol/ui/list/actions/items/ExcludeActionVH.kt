package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionExcludeItemBinding


class ExcludeActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<ExcludeActionVH.Item, AppcontrolActionExcludeItemBinding>(
        R.layout.appcontrol_action_exclude_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionExcludeItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionExcludeItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        itemView.setOnClickListener { item.onExclude(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onExclude: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}