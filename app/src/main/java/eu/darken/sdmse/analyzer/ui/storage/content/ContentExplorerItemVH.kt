package eu.darken.sdmse.analyzer.ui.storage.content

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerContentExplorerItemBinding


class ContentExplorerItemVH(parent: ViewGroup) :
    ContentExplorerAdapter.BaseVH<ContentExplorerItemVH.Item, AnalyzerContentExplorerItemBinding>(
        R.layout.analyzer_content_explorer_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerContentExplorerItemBinding.bind(itemView) }

    override val onBindData: AnalyzerContentExplorerItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
//        val app = item.app
//
//        appIcon.loadAppIcon(app.pkg)
//        primary.text = app.pkg.label?.get(context) ?: app.pkg.packageName
//        secondary.text = Formatter.formatShortFileSize(context, app.totalSize)

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val content: ContentItem,
        val onItemClicked: (Item) -> Unit,
    ) : ContentExplorerAdapter.Item {

        override val stableId: Long = content.path.hashCode().toLong()
    }

}