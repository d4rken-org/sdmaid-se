package eu.darken.sdmse.appcontrol.ui.list

import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.databinding.AppcontrolListItemBinding


class AppControlListRowVH(parent: ViewGroup) :
    AppControlListAdapter.BaseVH<AppControlListRowVH.Item, AppcontrolListItemBinding>(
        R.layout.appcontrol_list_item,
        parent
    ), SelectableVH {

    override val viewBinding = lazy { AppcontrolListItemBinding.bind(itemView) }

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val onBindData: AppcontrolListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val appInfo = item.appInfo
        icon.loadAppIcon(appInfo.pkg)
        primary.text = appInfo.label.get(context)
        secondary.text = appInfo.pkg.packageName

        tertiary.text = "${appInfo.pkg.versionName}  (${appInfo.pkg.versionCode})"

        tagSystem.tagSystem.isInvisible = !appInfo.pkg.isSystemApp
        tagDisabled.tagDisabled.isInvisible = appInfo.pkg.isEnabled
        tagActive.tagActive.isInvisible = !(appInfo.isActive ?: false)
        tagContainer.isGone = tagContainer.children.none { it.isVisible }

        itemView.setOnClickListener { item.onItemClicked(appInfo) }
    }

    data class Item(
        override val appInfo: AppInfo,
        val lablrName: String?,
        val lablrPkg: String?,
        val lablrUpdated: String?,
        val lablrInstalled: String?,
        val onItemClicked: (AppInfo) -> Unit,
    ) : AppControlListAdapter.Item, SelectableItem {

        override val itemSelectionKey: String
            get() = appInfo.installId.toString()

        override val stableId: Long = appInfo.installId.hashCode().toLong()
    }
}