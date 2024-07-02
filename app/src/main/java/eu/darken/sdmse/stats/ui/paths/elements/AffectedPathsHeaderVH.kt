package eu.darken.sdmse.stats.ui.paths.elements

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.StatsAffectedPathsHeaderItemBinding
import eu.darken.sdmse.main.core.iconRes
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.ui.paths.AffectedPathsAdapter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class AffectedPathsHeaderVH(parent: ViewGroup) :
    AffectedPathsAdapter.BaseVH<AffectedPathsHeaderVH.Item, StatsAffectedPathsHeaderItemBinding>(
        R.layout.stats_affected_paths_header_item,
        parent
    ) {

    override val viewBinding = lazy { StatsAffectedPathsHeaderItemBinding.bind(itemView) }

    override val onBindData: StatsAffectedPathsHeaderItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val report = item.report
        toolIcon.setImageResource(report.tool.iconRes)
        toolValue.text = getString(report.tool.labelRes)

        subtitle.apply {
            text = report.startAt.toSystemTimezone().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
            val duration = DateUtils.formatElapsedTime(report.duration.toSeconds())
            append(" ~ ($duration)")
        }

        countValue.text = report.affectedCount?.let {
            getQuantityString(eu.darken.sdmse.common.R.plurals.result_x_items, it)
        } ?: "?"

        sizeVaule.text = report.affectedSpace?.let {
            Formatter.formatShortFileSize(context, it)
        } ?: "?"
    }

    data class Item(
        val report: Report,
        val affectedPaths: Collection<AffectedPath>,
    ) : AffectedPathsAdapter.Item {

        override val stableId: Long = report.reportId.hashCode().toLong()
    }

}