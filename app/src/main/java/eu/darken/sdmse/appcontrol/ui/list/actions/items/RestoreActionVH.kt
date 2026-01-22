package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionRestoreItemBinding


class RestoreActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<RestoreActionVH.Item, AppcontrolActionRestoreItemBinding>(
        R.layout.appcontrol_action_restore_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionRestoreItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionRestoreItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        root.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(R.string.appcontrol_restore_confirmation_title)
                setMessage(
                    getString(
                        R.string.appcontrol_restore_description,
                    )
                )
                setPositiveButton(R.string.appcontrol_restore_action) { _, _ ->
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
