package eu.darken.sdmse.common.debug.recorder.ui

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DebugRecorderDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class DebugRecorderCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DebugRecorderCardVH.Item, DebugRecorderDashboardItemBinding>(
        R.layout.debug_recorder_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { DebugRecorderDashboardItemBinding.bind(itemView) }

    override val onBindData: DebugRecorderDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        title.text = getString(
            if (item.state.isRecording) R.string.debug_debuglog_recording_progress
            else R.string.support_debuglog_label
        )
        body.text = when {
            item.state.isRecording -> item.state.currentLogPath?.path
            else -> getString(R.string.support_debuglog_desc)
        }
        toggleRecordingAction.apply {
            text = getString(
                when {
                    item.state.isRecording -> R.string.debug_debuglog_stop_action
                    else -> R.string.debug_debuglog_record_action
                }
            )
            setOnClickListener {
                if (item.state.isRecording) {
                    item.onToggleRecording()
                } else {
                    RecorderConsentDialog(context, item.webpageTool).showDialog { item.onToggleRecording() }
                }
            }
        }
    }

    data class Item(
        val webpageTool: WebpageTool,
        val state: RecorderModule.State,
        val onToggleRecording: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}