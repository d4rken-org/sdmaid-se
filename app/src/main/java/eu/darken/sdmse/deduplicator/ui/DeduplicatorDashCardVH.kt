package eu.darken.sdmse.deduplicator.ui

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.DeduplicatorDashboardItemBinding
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.main.ui.dashboard.MainActionItem


class DeduplicatorDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DeduplicatorDashCardVH.Item, DeduplicatorDashboardItemBinding>(
        R.layout.deduplicator_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { DeduplicatorDashboardItemBinding.bind(itemView) }

    override val onBindData: DeduplicatorDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        activityContainer.isGone = item.progress == null && item.data == null
        progressBar.isInvisible = item.progress == null
        statusPrimary.isInvisible = item.progress != null
        statusSecondary.isInvisible = item.progress != null


        if (item.progress != null) {
            progressBar.setProgress(item.progress)
        } else if (item.data != null) {
            statusPrimary.text = getQuantityString(
                R.plurals.deduplicator_result_x_clusters_found,
                item.data.totalCount
            )
            val space = Formatter.formatFileSize(context, item.data.redudantSize)
            statusSecondary.text = getString(R.string.deduplicator_x_space_occupied_by_duplicates_msg, space)
        } else {
            statusPrimary.text = null
            statusSecondary.text = null
        }

        val hasAnyData = item.data.hasData

        detailsAction.apply {
            isGone = item.progress != null || !hasAnyData
            setOnClickListener { item.onViewDetails() }
        }

        scanAction.apply {
            if (!hasAnyData) {
                text = getString(eu.darken.sdmse.common.R.string.general_scan_action)
                iconPadding = context.dpToPx(4f)
            } else {
                text = null
                iconPadding = 0
            }
            isGone = item.progress != null
            setOnClickListener { item.onScan() }
        }
        deleteAction.apply {
            isGone = item.progress != null || !hasAnyData
            setOnClickListener { item.onDelete() }
        }
        cancelAction.apply {
            isGone = item.progress == null
            setOnClickListener { item.onCancel() }
        }

        itemView.apply {
            setOnClickListener { item.onViewDetails() }
            isClickable = item.progress == null && hasAnyData
        }
    }

    data class Item(
        val data: Deduplicator.Data?,
        val progress: Progress.Data?,
        val onScan: () -> Unit,
        val onDelete: () -> Unit,
        val onViewDetails: () -> Unit,
        val onCancel: () -> Unit,
    ) : DashboardAdapter.Item, MainActionItem {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}