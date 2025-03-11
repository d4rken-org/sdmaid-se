package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionAdapter
import eu.darken.sdmse.common.formatDuration
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.AppcontrolActionInfoUsageItemBinding
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class InfoUsageVH(parent: ViewGroup) :
    AppActionAdapter.BaseVH<InfoUsageVH.Item, AppcontrolActionInfoUsageItemBinding>(
        R.layout.appcontrol_action_info_usage_item,
        parent
    ) {

    override val viewBinding = lazy { AppcontrolActionInfoUsageItemBinding.bind(itemView) }

    override val onBindData: AppcontrolActionInfoUsageItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appInfo = item.appInfo

        val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        installedAt.text = getString(
            R.string.appcontrol_item_installedat_x_label,
            appInfo.installedAt?.toSystemTimezone()?.format(dateFormatter)
                ?: getString(eu.darken.sdmse.common.R.string.general_na_label)
        )

        updatedAt.text = getString(
            R.string.appcontrol_item_lastupdate_x_label,
            appInfo.updatedAt?.toSystemTimezone()?.format(dateFormatter)
                ?: getString(eu.darken.sdmse.common.R.string.general_na_label)
        )

        screenTime.apply {
            isGone = appInfo.usage == null
            text = if (appInfo.usage == null) {
                context.getString(eu.darken.sdmse.common.R.string.general_na_label)
            } else {
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val since = appInfo.usage.screenTimeSince.toSystemTimezone().format(formatter)
                val durationTxt = appInfo.usage.screenTime.formatDuration(abbrev = DateUtils.LENGTH_LONG)
                getString(R.string.appcontrol_item_screentime_x_since_y_label, durationTxt, since)
            }
        }

        itemView.setOnClickListener { item.onClicked(appInfo) }
    }

    data class Item(
        val appInfo: AppInfo,
        val onClicked: (AppInfo) -> Unit,
    ) : AppActionAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}