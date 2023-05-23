package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerContentItemVhBinding


class ContentItemVH(parent: ViewGroup) :
    ContentAdapter.BaseVH<ContentItemVH.Item, AnalyzerContentItemVhBinding>(
        R.layout.analyzer_content_item_vh,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerContentItemVhBinding.bind(itemView) }

    override val onBindData: AnalyzerContentItemVhBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val content = item.content

        // TODO can we load previews for some files?
        contentIcon.setImageResource(
            when (content.type) {
                FileType.DIRECTORY -> R.drawable.ic_folder
                FileType.SYMBOLIC_LINK -> R.drawable.ic_file_link
                FileType.FILE -> R.drawable.ic_file
                FileType.UNKNOWN -> R.drawable.file_question
            }
        )

        primary.text = if (item.parent != null) {
            content.path.segments.drop(item.parent.path.segments.size).single()
        } else {
            content.label.get(context)
        }
        secondary.text = content.size
            ?.let { Formatter.formatShortFileSize(context, it) }
            ?: getString(R.string.analyzer_content_access_opaque)

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val parent: ContentItem?,
        val content: ContentItem,
        val onItemClicked: (Item) -> Unit,
    ) : ContentAdapter.Item {

        override val stableId: Long = content.path.hashCode().toLong()
    }

}