package eu.darken.sdmse.appcontrol.ui.list

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
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
        val app = item.appInfo
        icon.loadAppIcon(app.pkg)
        primary.text = app.label.get(context)
        secondary.text = app.pkg.packageName

        primary.append(" (${app.pkg.versionName ?: app.pkg.versionCode})")
//
//        items.text = getQuantityString(R.plurals.result_x_items, junk.itemCount)
//        size.text = Formatter.formatShortFileSize(context, junk.size)
//
//        root.setOnClickListener { item.onItemClicked(junk) }
//        detailsAction.setOnClickListener { item.onDetailsClicked(junk) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onItemClicked: (AppJunk) -> Unit,
    ) : AppControlListAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}