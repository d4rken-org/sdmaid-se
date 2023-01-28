package eu.darken.sdmse.appcontrol.ui.list

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcontrolListItemBinding


class AppControlListRowVH(parent: ViewGroup) :
    AppControlListAdapter.BaseVH<AppControlListRowVH.Item, AppcontrolListItemBinding>(
        R.layout.appcontrol_list_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolListItemBinding.bind(itemView) }

    override val onBindData: AppcontrolListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo
        icon.loadAppIcon(appInfo.pkg)
        primary.text = appInfo.label.get(context)
        secondary.text = appInfo.pkg.packageName

        primary.append(" (${appInfo.pkg.versionName ?: appInfo.pkg.versionCode})")
//
//        items.text = getQuantityString(R.plurals.result_x_items, junk.itemCount)
//        size.text = Formatter.formatShortFileSize(context, junk.size)
//
        root.setOnClickListener { item.onItemClicked(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onItemClicked: (AppInfo) -> Unit,
    ) : AppControlListAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}