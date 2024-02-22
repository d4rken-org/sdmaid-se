package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionBackupItemBinding


class ExportActionVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<ExportActionVH.Item, AppcontrolActionBackupItemBinding>(
        R.layout.appcontrol_action_backup_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionBackupItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionBackupItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        itemView.setOnClickListener { item.onBackup(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onBackup: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}