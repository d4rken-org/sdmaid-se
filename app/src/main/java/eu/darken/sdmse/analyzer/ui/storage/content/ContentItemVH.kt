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

        secondary.text = when (content.type) {
            FileType.DIRECTORY -> content.size?.let {
                val sizeFormatted = Formatter.formatShortFileSize(context, it)
                val itemsFormatted = getQuantityString(
                    eu.darken.sdmse.common.R.plurals.result_x_items,
                    content.children?.size ?: -1
                )
                "$sizeFormatted ($itemsFormatted)"
            } ?: "?"

            else -> content.size?.let {
                Formatter.formatShortFileSize(context, it)
            } ?: "?"
        }

        tertiary.text = when (content.type) {
            FileType.DIRECTORY -> getString(eu.darken.sdmse.common.R.string.file_type_directory)
            FileType.FILE -> getString(eu.darken.sdmse.common.R.string.file_type_file)
            FileType.SYMBOLIC_LINK -> getString(eu.darken.sdmse.common.R.string.file_type_symbolic_link)
            FileType.UNKNOWN -> getString(eu.darken.sdmse.common.R.string.file_type_unknown)
        }

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