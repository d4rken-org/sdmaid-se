package eu.darken.sdmse.stats.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.StatsDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.stats.core.StatsRepo


class StatsDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<StatsDashCardVH.Item, StatsDashboardItemBinding>(
        R.layout.stats_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { StatsDashboardItemBinding.bind(itemView) }

    override val onBindData: StatsDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val (space, spaceQuantity) = ByteFormatter.formatSize(context, item.state.totalSpaceFreed)
        val spaceFormatted = getQuantityString(
            R.plurals.stats_dash_body_size,
            spaceQuantity,
            space
        )

        val processed = item.state.itemsProcessed.toString()
        val processedQuantity = item.state.itemsProcessed
        val processedFormatted = getQuantityString(
            R.plurals.stats_dash_body_count,
            processedQuantity.toInt(),
            processed,
        )
        val wholeText = "$spaceFormatted $processedFormatted"

        body.text = SpannableString(wholeText).apply {
            val startFreed = wholeText.indexOf(space)
            val endFreed = startFreed + space.length
            setSpan(
                ForegroundColorSpan(getColorForAttr(com.google.android.material.R.attr.colorPrimary)),
                startFreed,
                endFreed,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val startProcessed = wholeText.indexOf(processedFormatted)
            val endProcessed = startProcessed + processed.length
            setSpan(
                ForegroundColorSpan(getColorForAttr(com.google.android.material.R.attr.colorPrimary)),
                startProcessed,
                endProcessed,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        viewAction.apply {
            if (item.showProRequirement) {
                setIconResource(R.drawable.ic_baseline_stars_24)
            } else {
                icon = null
            }
        }
        root.setOnClickListener { item.onViewAction() }
    }

    data class Item(
        val state: StatsRepo.State,
        val showProRequirement: Boolean,
        val onViewAction: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}