package eu.darken.sdmse.main.ui.dashboard

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.DashboardToolCardBinding
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.R as CommonR

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
                SDMTool.Type.CORPSEFINDER -> CommonR.drawable.ghost
                SDMTool.Type.SYSTEMCLEANER -> CommonR.drawable.ic_baseline_view_list_24
                SDMTool.Type.APPCLEANER -> CommonR.drawable.ic_recycle
                SDMTool.Type.DEDUPLICATOR -> CommonR.drawable.ic_content_duplicate_24
                SDMTool.Type.APPCONTROL, SDMTool.Type.ANALYZER, SDMTool.Type.SQUEEZER, SDMTool.Type.SWIPER -> 0
            }
        )
        title.setText(
            when (item.toolType) {
                SDMTool.Type.CORPSEFINDER -> eu.darken.sdmse.common.R.string.corpsefinder_tool_name
                SDMTool.Type.SYSTEMCLEANER -> eu.darken.sdmse.common.R.string.systemcleaner_tool_name
                SDMTool.Type.APPCLEANER -> eu.darken.sdmse.common.R.string.appcleaner_tool_name
                SDMTool.Type.DEDUPLICATOR -> eu.darken.sdmse.common.R.string.deduplicator_tool_name
                SDMTool.Type.APPCONTROL, SDMTool.Type.ANALYZER, SDMTool.Type.SQUEEZER, SDMTool.Type.SWIPER -> 0
            }
        )
        description.apply {
            setText(
                when (item.toolType) {
                    SDMTool.Type.CORPSEFINDER -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_explanation_short
                    SDMTool.Type.SYSTEMCLEANER -> eu.darken.sdmse.systemcleaner.R.string.systemcleaner_explanation_short
                    SDMTool.Type.APPCLEANER -> eu.darken.sdmse.appcleaner.R.string.appcleaner_explanation_short
                    SDMTool.Type.DEDUPLICATOR -> eu.darken.sdmse.deduplicator.R.string.deduplicator_explanation_short
SDMTool.Type.APPCONTROL, SDMTool.Type.ANALYZER, SDMTool.Type.SQUEEZER, SDMTool.Type.SWIPER -> 0
                }
            )
            isGone = item.progress != null || item.result != null
        }

        toolLoadingIndicator.isGone = !item.isInitializing

        activityContainer.apply {
            isGone = item.progress == null && item.result == null
            setOnClickListener { item.onViewTool() }
            isFocusable = item.result != null && item.progress == null
            isClickable = item.result != null && item.progress == null
        }
        val resultPrimary = item.result?.primaryInfo?.get(context)
        val resultSecondary = item.result?.secondaryInfo?.get(context)?.takeUnless { it.isBlank() }

        progressBar.isGone = item.progress == null
        statusPrimary.isGone = item.progress != null || resultPrimary.isNullOrBlank()
        statusSecondary.isGone = item.progress != null || resultSecondary == null

        if (item.progress != null) {
            progressBar.setProgress(item.progress)
        } else if (item.result != null) {
            statusPrimary.text = resultPrimary
            statusSecondary.text = resultSecondary
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
            isEnabled = !item.isInitializing
        }
        deleteAction.apply {
            isGone = item.progress != null || item.onDelete == null
            setOnClickListener { item.onDelete?.invoke() }
            setText(eu.darken.sdmse.common.R.string.general_delete_action)
            if (item.showProRequirement) {
                setIconResource(UiR.drawable.ic_baseline_stars_24)
            } else if (item.onDelete != null) {
                setIconResource(UiR.drawable.ic_delete)
            } else {
                icon = null
            }
            isEnabled = !item.isInitializing
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
        val isInitializing: Boolean,
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
