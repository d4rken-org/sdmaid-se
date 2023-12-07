package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolActionSizeItemBinding


class SizeInfoVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<SizeInfoVH.Item, AppcontrolActionSizeItemBinding>(
        R.layout.appcontrol_action_size_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionSizeItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionSizeItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo
        val sizes = appInfo.sizes!!

        totalValue.text = Formatter.formatFileSize(context, sizes.total)
        apkSize.text = Formatter.formatFileSize(context, sizes.appBytes)
        dataSize.text = Formatter.formatFileSize(context, sizes.dataBytes)
        cacheSize.text = Formatter.formatFileSize(context, sizes.cacheBytes)

        itemView.setOnClickListener { item.onSizeClicked(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onSizeClicked: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}