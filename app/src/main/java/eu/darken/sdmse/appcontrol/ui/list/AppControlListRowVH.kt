package eu.darken.sdmse.appcontrol.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.AppcontrolListItemBinding
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


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

    private val usageDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    private val installFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    override val onBindData: AppcontrolListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val appInfo = item.appInfo
        icon.loadAppIcon(appInfo.pkg)
        label.text = appInfo.label.get(context)
        packagename.text = appInfo.pkg.packageName

        extraInfo.text = when (item.sortMode) {
            SortSettings.Mode.INSTALLED_AT -> getString(
                R.string.appcontrol_item_installedat_x_label,
                appInfo.installedAt?.toSystemTimezone()?.format(installFormatter)
                    ?: getString(eu.darken.sdmse.common.R.string.general_na_label)
            )

            SortSettings.Mode.LAST_UPDATE -> getString(
                R.string.appcontrol_item_lastupdate_x_label,
                appInfo.updatedAt?.toSystemTimezone()?.format(installFormatter)
                    ?: getString(eu.darken.sdmse.common.R.string.general_na_label)
            )

            SortSettings.Mode.SCREEN_TIME -> if (appInfo.usage == null) {
                context.getString(eu.darken.sdmse.common.R.string.general_na_label)
            } else {
                val since = appInfo.usage.screenTimeSince.toSystemTimezone().format(usageDateFormatter)
                val durationTxt = item.lablrScreenTime ?: "?"
                getString(R.string.appcontrol_item_screentime_x_since_y_label, durationTxt, since)
            }

            else -> "${appInfo.pkg.versionName ?: "?"}  (${appInfo.pkg.versionCode})"
        }

        sizes.apply {
            text = appInfo.sizes?.let { Formatter.formatShortFileSize(context, it.total) }
            isVisible = appInfo.sizes != null
        }

        tagContainer.setPkg(appInfo)

        itemView.setOnClickListener { item.onItemClicked(appInfo) }
    }

    data class Item(
        override val appInfo: AppInfo,
        val sortMode: SortSettings.Mode,
        val lablrName: String?,
        val lablrPkg: String?,
        val lablrUpdated: String?,
        val lablrInstalled: String?,
        val lablrSize: String?,
        val lablrScreenTime: String?,
        val onItemClicked: (AppInfo) -> Unit,
    ) : AppControlListAdapter.Item, SelectableItem {

        override val itemSelectionKey: String
            get() = appInfo.installId.toString()

        override val stableId: Long = appInfo.installId.hashCode().toLong()
    }
}