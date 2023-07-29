package eu.darken.sdmse.analyzer.ui.storage.storage.categories

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentAdapter
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerStorageVhSystemBinding
import kotlin.math.roundToInt


class SystemCategoryVH(parent: ViewGroup) :
    StorageContentAdapter.BaseVH<SystemCategoryVH.Item, AnalyzerStorageVhSystemBinding>(
        R.layout.analyzer_storage_vh_system,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerStorageVhSystemBinding.bind(itemView) }

    override val onBindData: AnalyzerStorageVhSystemBinding.(
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

        root.setOnClickListener { item.onItemClick() }
    }

    data class Item(
        val storage: DeviceStorage,
        val content: SystemCategory,
        val onItemClick: () -> Unit,
    ) : StorageContentAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}