package eu.darken.sdmse.analyzer.ui.storage.storage.categories

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentAdapter
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerStorageVhMediaBinding
import kotlin.math.roundToInt


class MediaCategoryVH(parent: ViewGroup) :
    StorageContentAdapter.BaseVH<MediaCategoryVH.Item, AnalyzerStorageVhMediaBinding>(
        R.layout.analyzer_storage_vh_media,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerStorageVhMediaBinding.bind(itemView) }

    override val onBindData: AnalyzerStorageVhMediaBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val content = item.content
        val storage = item.storage

        val usedText = Formatter.formatShortFileSize(context, content.spaceUsed)
        val totalPercent = ((content.spaceUsed / storage.spaceUsed.toDouble()) * 100).toInt()
        usedSpace.text = getQuantityString(
            R.plurals.analyzer_space_used,
            ByteFormatter.stripSizeUnit(usedText)?.roundToInt() ?: 1,
            usedText
        )
        progress.progress = totalPercent

        root.setOnClickListener { item.onItemClicked(content) }
    }

    data class Item(
        val storage: DeviceStorage,
        val content: MediaCategory,
        val onItemClicked: (MediaCategory) -> Unit,
    ) : StorageContentAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}