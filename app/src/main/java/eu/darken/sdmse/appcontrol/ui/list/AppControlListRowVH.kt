package eu.darken.sdmse.appcontrol.ui.list

import android.view.ViewGroup
import androidx.core.view.isInvisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
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

        tertiary.text = "${appInfo.pkg.versionName}  (${appInfo.pkg.versionCode})"

        tagSystem.tagSystem.isInvisible = !appInfo.pkg.isSystemApp
        tagDisabled.tagDisabled.isInvisible = appInfo.pkg.isEnabled
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