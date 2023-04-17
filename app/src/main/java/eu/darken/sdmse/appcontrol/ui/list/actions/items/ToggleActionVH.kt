package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.databinding.AppcontrolActionToggleItemBinding


class ToggleActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<ToggleActionVH.Item, AppcontrolActionToggleItemBinding>(
        R.layout.appcontrol_action_toggle_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionToggleItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionToggleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        if (appInfo.pkg.isEnabled) {
            icon.setImageResource(R.drawable.ic_snowflake_24)
            primary.text = getString(R.string.appcontrol_toggle_app_disable_action)
            secondary.text = getString(R.string.appcontrol_toggle_app_disable_description)
        } else {
            icon.setImageResource(R.drawable.ic_snowflake_off_24)
            primary.text = getString(R.string.appcontrol_toggle_app_enable_action)
            secondary.text = getString(R.string.appcontrol_toggle_app_enable_description)
        }

        itemView.setOnClickListener { item.onToggle(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onToggle: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}