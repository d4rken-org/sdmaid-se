package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        root.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                setMessage(
                    getString(
                        eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                        appInfo.label.get(context)
                    )
                )
                setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                    item.onItemClicked(appInfo)
                }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            }.show()
        }
    }

    data class Item(
        val appInfo: AppInfo,
        val onItemClicked: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}