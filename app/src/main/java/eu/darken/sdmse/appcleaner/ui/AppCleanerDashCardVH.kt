package eu.darken.sdmse.appcleaner.ui

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.AppcleanerDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class AppCleanerDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<AppCleanerDashCardVH.Item, AppcleanerDashboardItemBinding>(
        R.layout.appcleaner_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { AppcleanerDashboardItemBinding.bind(itemView) }

    override val onBindData: AppcleanerDashboardItemBinding.(
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
                R.plurals.appcleaner_result_x_items_found,
                item.data.junks.sumOf { it.itemCount }
            )
            val space = Formatter.formatFileSize(context, item.data.totalSize)
            statusSecondary.text = getString(R.string.x_space_can_be_freed, space)
        } else {
            statusPrimary.text = null
            statusSecondary.text = null
        }

        val hasAnyData = item.data.hasData

        detailsAction.apply {
            isGone = item.progress != null || !hasAnyData
            setOnClickListener { item.onViewDetails() }
        }
        if (item.progress == null && hasAnyData) {
            activityContainer.setOnClickListener { item.onViewDetails() }
        } else {
            activityContainer.setOnClickListener(null)
        }

        scanAction.apply {
            isGone = item.progress != null
            setOnClickListener { item.onScan() }
        }
        deleteAction.apply {
            isGone = item.progress != null || !hasAnyData
            setOnClickListener { item.onDelete() }
            if (!item.isPro) {
                setIconResource(R.drawable.ic_baseline_stars_24)
            }
        }
        cancelAction.apply {
            isGone = item.progress == null
            setOnClickListener { item.onCancel() }
        }
    }

    data class Item(
        val data: AppCleaner.Data?,
        val progress: Progress.Data?,
        val isPro: Boolean,
        val onScan: () -> Unit,
        val onDelete: () -> Unit,
        val onViewDetails: () -> Unit,
        val onCancel: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}