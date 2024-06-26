package eu.darken.sdmse.main.ui.dashboard

import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.DashboardToolCardBinding
import eu.darken.sdmse.main.core.SDMTool

class DashboardToolCard(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DashboardToolCard.Item, DashboardToolCardBinding>(
        R.layout.dashboard_tool_card,
        parent
    ) {

    override val viewBinding = lazy { DashboardToolCardBinding.bind(itemView) }

    override val onBindData: DashboardToolCardBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        icon.setImageResource(
            when (item.toolType) {
                SDMTool.Type.CORPSEFINDER -> R.drawable.ghost
                SDMTool.Type.SYSTEMCLEANER -> R.drawable.ic_baseline_view_list_24
                SDMTool.Type.APPCLEANER -> R.drawable.ic_recycle
                SDMTool.Type.DEDUPLICATOR -> R.drawable.ic_content_duplicate_24
                SDMTool.Type.APPCONTROL, SDMTool.Type.ANALYZER -> 0
            }
        )
        title.setText(
            when (item.toolType) {
                SDMTool.Type.CORPSEFINDER -> R.string.corpsefinder_tool_name
                SDMTool.Type.SYSTEMCLEANER -> R.string.systemcleaner_tool_name
                SDMTool.Type.APPCLEANER -> R.string.appcleaner_tool_name
                SDMTool.Type.DEDUPLICATOR -> R.string.deduplicator_tool_name
                SDMTool.Type.APPCONTROL, SDMTool.Type.ANALYZER -> 0
            }
        )
        description.apply {
            setText(
                when (item.toolType) {
                    SDMTool.Type.CORPSEFINDER -> R.string.corpsefinder_explanation_short
                    SDMTool.Type.SYSTEMCLEANER -> R.string.systemcleaner_explanation_short
                    SDMTool.Type.APPCLEANER -> R.string.appcleaner_explanation_short
                    SDMTool.Type.DEDUPLICATOR -> R.string.deduplicator_explanation_short
                    SDMTool.Type.APPCONTROL, SDMTool.Type.ANALYZER -> 0
                }
            )
            isGone = item.progress != null || item.result != null
        }

        activityContainer.isGone = item.progress == null && item.result == null
        progressBar.isInvisible = item.progress == null
        statusPrimary.isInvisible = item.progress != null
        statusSecondary.isInvisible = item.progress != null

        if (item.progress != null) {
            progressBar.setProgress(item.progress)
        } else if (item.result != null) {
            statusPrimary.text = item.result.primaryInfo.get(context)
            statusSecondary.text = item.result.secondaryInfo?.get(context)
        } else {
            statusPrimary.text = null
            statusSecondary.text = null
        }

        detailsAction.apply {
            isGone = item.progress != null || item.onDelete == null
            setOnClickListener { item.onViewDetails() }
        }

        scanAction.apply {
            if (item.onDelete == null) {
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
            isGone = item.progress != null || item.onDelete == null
            setOnClickListener { item.onDelete?.invoke() }
            if (item.showProRequirement) {
                setIconResource(R.drawable.ic_baseline_stars_24)
            } else if (item.onDelete != null) {
                setIconResource(R.drawable.ic_delete)
            } else {
                icon = null
            }
        }
        cancelAction.apply {
            isGone = item.progress == null
            setOnClickListener { item.onCancel() }
        }

        itemView.apply {
            setOnClickListener { item.onViewTool() }
            isClickable = item.progress == null && item.onDelete != null
        }
    }

    data class Item(
        val toolType: SDMTool.Type,
        val result: SDMTool.Task.Result?,
        val progress: Progress.Data?,
        val showProRequirement: Boolean,
        val onScan: () -> Unit,
        val onDelete: (() -> Unit)?,
        val onViewTool: () -> Unit,
        val onViewDetails: () -> Unit,
        val onCancel: () -> Unit,
    ) : DashboardAdapter.Item, MainActionItem {
        override val stableId: Long = toolType.hashCode().toLong()
    }
}