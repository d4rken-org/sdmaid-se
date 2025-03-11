package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.databinding.AppcontrolActionInfoSizeItemBinding


class InfoSizeVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<InfoSizeVH.Item, AppcontrolActionInfoSizeItemBinding>(
        R.layout.appcontrol_action_info_size_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionInfoSizeItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionInfoSizeItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo
        val sizes = appInfo.sizes!!

        totalValue.text = Formatter.formatFileSize(context, sizes.total)
        apkSize.apply {
            text = Formatter.formatFileSize(context, sizes.appBytes)
            isVisible = appInfo.pkg is SourceAvailable
        }
        apkLabel.isGone = apkSize.isGone
        dataSize.text = Formatter.formatFileSize(context, sizes.dataBytes)
        cacheSize.text = Formatter.formatFileSize(context, sizes.cacheBytes)

        itemView.setOnClickListener { item.onClicked(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onClicked: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}