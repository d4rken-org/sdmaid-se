package eu.darken.sdmse.analyzer.ui.storage.content

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerContentVhBinding


class ContentItemVH(parent: ViewGroup) :
    ContentAdapter.BaseVH<ContentItemVH.Item, AnalyzerContentVhBinding>(
        R.layout.analyzer_content_vh,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerContentVhBinding.bind(itemView) }

    override val onBindData: AnalyzerContentVhBinding.(
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
    ) : ContentAdapter.Item {

        override val stableId: Long = content.path.hashCode().toLong()
    }

}