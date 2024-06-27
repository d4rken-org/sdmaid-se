package eu.darken.sdmse.stats.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import eu.darken.sdmse.R
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
        val spaceFreed = Formatter.formatShortFileSize(context, item.state.totalSpaceFreed)
        val itemsProcessed = item.state.itemsProcessed.toString()
        val wholeText = getString(R.string.stats_dash_body, spaceFreed, itemsProcessed)

        body.text = SpannableString(wholeText).apply {
            val startFreed = wholeText.indexOf(spaceFreed)
            val endFreed = startFreed + spaceFreed.length
            setSpan(
                ForegroundColorSpan(getColorForAttr(com.google.android.material.R.attr.colorPrimary)),
                startFreed,
                endFreed,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val startProcessed = wholeText.indexOf(itemsProcessed)
            val endProcessed = startProcessed + itemsProcessed.length
            setSpan(
                ForegroundColorSpan(getColorForAttr(com.google.android.material.R.attr.colorPrimary)),
                startProcessed,
                endProcessed,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        root.setOnClickListener { viewAction.performClick() }
        viewAction.apply { setOnClickListener { item.onViewAction() } }
    }

    data class Item(
        val state: StatsRepo.State,
        val onViewAction: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}