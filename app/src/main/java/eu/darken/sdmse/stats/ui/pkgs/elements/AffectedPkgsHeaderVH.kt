package eu.darken.sdmse.stats.ui.pkgs.elements

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isInvisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.StatsAffectedPkgsHeaderItemBinding
import eu.darken.sdmse.main.core.iconRes
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.ui.pkgs.AffectedPkgsAdapter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class AffectedPkgsHeaderVH(parent: ViewGroup) :
    AffectedPkgsAdapter.BaseVH<AffectedPkgsHeaderVH.Item, StatsAffectedPkgsHeaderItemBinding>(
        R.layout.stats_affected_pkgs_header_item,
        parent
    ) {

    override val viewBinding = lazy { StatsAffectedPkgsHeaderItemBinding.bind(itemView) }

    override val onBindData: StatsAffectedPkgsHeaderItemBinding.(
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

        sizeLabel.isInvisible = report.affectedSpace == null
        sizeVaule.isInvisible = report.affectedSpace == null
    }

    data class Item(
        val report: Report,
        val affectedPkgs: Collection<AffectedPkg>,
    ) : AffectedPkgsAdapter.Item {
        override val stableId: Long = report.reportId.hashCode().toLong()
    }
}