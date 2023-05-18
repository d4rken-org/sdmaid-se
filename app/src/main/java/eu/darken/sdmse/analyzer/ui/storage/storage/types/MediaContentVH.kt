package eu.darken.sdmse.analyzer.ui.storage.storage.types

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.types.MediaContent
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerStorageContentMediaItemBinding


class MediaContentVH(parent: ViewGroup) :
    StorageContentAdapter.BaseVH<MediaContentVH.Item, AnalyzerStorageContentMediaItemBinding>(
        R.layout.analyzer_storage_content_media_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerStorageContentMediaItemBinding.bind(itemView) }

    override val onBindData: AnalyzerStorageContentMediaItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val content = item.content
        val storage = item.storage

        val usedText = Formatter.formatShortFileSize(context, content.spaceUsed)
        val totalPercent = ((content.spaceUsed / storage.spaceUsed.toDouble()) * 100).toInt()
        usedSpace.text = getString(R.string.analyzer_storage_content_x_used_of_total_y, usedText, "$totalPercent%")
        progress.progress = totalPercent

        root.setOnClickListener { item.onItemClicked(content) }
    }

    data class Item(
        val storage: DeviceStorage,
        val content: MediaContent,
        val onItemClicked: (MediaContent) -> Unit,
    ) : StorageContentAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}