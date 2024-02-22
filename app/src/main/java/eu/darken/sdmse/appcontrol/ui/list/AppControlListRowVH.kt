package eu.darken.sdmse.appcontrol.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.export.AppExportType
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.debug.Bugs
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

        @Suppress("SetTextI18n")
        tertiary.text = "${appInfo.pkg.versionName}  (${appInfo.pkg.versionCode})"

        sizes.apply {
            text = appInfo.sizes?.let { Formatter.formatShortFileSize(context, it.total) }
            isVisible = appInfo.sizes != null
        }

        tagSystem.tagSystem.isGone = !appInfo.pkg.isSystemApp
        tagDisabled.tagDisabled.isGone = appInfo.pkg.isEnabled
        tagActive.tagActive.isGone = appInfo.isActive != true
        tagApkBase.tagApkBase.isGone = appInfo.exportType != AppExportType.APK && Bugs.isDebug
        tagApkBundle.tagApkBundle.isGone = appInfo.exportType != AppExportType.BUNDLE && Bugs.isDebug
        tagContainer.isGone = tagContainer.children.none { it.isVisible }

        itemView.setOnClickListener { item.onItemClicked(appInfo) }
    }

    data class Item(
        override val appInfo: AppInfo,
        val lablrName: String?,
        val lablrPkg: String?,
        val lablrUpdated: String?,
        val lablrInstalled: String?,
        val lablrSize: String?,
        val onItemClicked: (AppInfo) -> Unit,
    ) : AppControlListAdapter.Item, SelectableItem {

        override val itemSelectionKey: String
            get() = appInfo.installId.toString()

        override val stableId: Long = appInfo.installId.hashCode().toLong()
    }
}