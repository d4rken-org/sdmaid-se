package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionExcludeItemBinding
import eu.darken.sdmse.exclusion.core.types.Exclusion


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
        if (item.exclusion == null) {
            icon.setImageResource(R.drawable.ic_shield_24)
            primary.text = getString(R.string.appcontrol_app_exclude_add_title)
            secondary.text = getString(R.string.appcontrol_app_exclude_add_description)
        } else {
            icon.setImageResource(R.drawable.ic_shield_edit_24)
            primary.text = getString(R.string.appcontrol_app_exclude_edit_title)
            secondary.text = getString(R.string.appcontrol_app_exclude_edit_description)
        }
        itemView.setOnClickListener { item.onExclude(item.exclusion) }
    }

    data class Item(
        val appInfo: AppInfo,
        val exclusion: Exclusion.Pkg?,
        val onExclude: (Exclusion.Pkg?) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}