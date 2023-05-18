package eu.darken.sdmse.analyzer.ui.storage.storage.types

import android.text.format.Formatter
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.types.SystemContent
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerStorageContentSystemItemBinding


class SystemContentVH(parent: ViewGroup) :
    StorageContentAdapter.BaseVH<SystemContentVH.Item, AnalyzerStorageContentSystemItemBinding>(
        R.layout.analyzer_storage_content_system_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerStorageContentSystemItemBinding.bind(itemView) }

    override val onBindData: AnalyzerStorageContentSystemItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val content = item.content
        val storage = item.storage

        val usedText = Formatter.formatShortFileSize(context, content.spaceUsed)
        val totalPercent = ((content.spaceUsed / storage.spaceUsed.toDouble()) * 100).toInt()
        usedSpace.text = getString(R.string.analyzer_storage_content_x_used_of_total_y, usedText, "$totalPercent%")
        progress.progress = totalPercent

        root.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setMessage(R.string.analyzer_storage_content_cant_touch_type)
            }.show()
        }
    }

    data class Item(
        val storage: DeviceStorage,
        val content: SystemContent,
    ) : StorageContentAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}