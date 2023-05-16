package eu.darken.sdmse.analyzer.ui.storage.content.types

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.types.AppContent
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.content.StorageContentAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerStorageContentAppsItemBinding


class AppContentVH(parent: ViewGroup) :
    StorageContentAdapter.BaseVH<AppContentVH.Item, AnalyzerStorageContentAppsItemBinding>(
        R.layout.analyzer_storage_content_apps_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerStorageContentAppsItemBinding.bind(itemView) }

    override val onBindData: AnalyzerStorageContentAppsItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val storage = item.storage
        val content = item.content

        val usedText = Formatter.formatShortFileSize(context, content.spaceUsed)
        val totalPercent = ((content.spaceUsed / storage.spaceUsed.toDouble()) * 100).toInt()
        usedSpace.text = getString(R.string.analyzer_storage_content_x_used_of_total_y, usedText, "$totalPercent%")
        progress.progress = totalPercent

        root.setOnClickListener { item.onItemClicked(content) }
    }

    data class Item(
        val storage: DeviceStorage,
        val content: AppContent,
        val onItemClicked: (AppContent) -> Unit,
    ) : StorageContentAdapter.Item {

        override val stableId: Long = content.id.hashCode().toLong()
    }

}