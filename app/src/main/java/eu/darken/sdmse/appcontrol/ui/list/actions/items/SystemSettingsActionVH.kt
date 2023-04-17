package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionSystemSettingsItemBinding


class SystemSettingsActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<SystemSettingsActionVH.Item, AppcontrolActionSystemSettingsItemBinding>(
        R.layout.appcontrol_action_system_settings_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionSystemSettingsItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionSystemSettingsItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        itemView.setOnClickListener { item.onSettings(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onSettings: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}