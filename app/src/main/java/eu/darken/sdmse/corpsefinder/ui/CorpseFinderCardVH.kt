package eu.darken.sdmse.corpsefinder.ui

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.databinding.CorpsefinderDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class CorpseFinderCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<CorpseFinderCardVH.Item, CorpsefinderDashboardItemBinding>(
        R.layout.corpsefinder_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { CorpsefinderDashboardItemBinding.bind(itemView) }

    override val onBindData: CorpsefinderDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        if (item.progress != null) {
            statusPrimary.text = item.progress.primary.get(context)
            statusSecondary.text = item.progress.secondary.get(context)
        } else if (item.data != null) {
            statusPrimary.text = getQuantityString(R.plurals.result_x_items, item.data.corpses.size)
            val space = Formatter.formatFileSize(context, item.data.totalSize)
            statusSecondary.text = getString(R.string.x_space_can_be_freed, space)
        } else {
            statusPrimary.text = null
            statusSecondary.text = null
        }

        scanAction.setOnClickListener { item.onScan() }
        deleteAction.setOnClickListener { item.onDelete }
    }

    data class Item(
        val data: CorpseFinder.Data?,
        val progress: Progress.Data?,
        val onScan: () -> Unit,
        val onDelete: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}