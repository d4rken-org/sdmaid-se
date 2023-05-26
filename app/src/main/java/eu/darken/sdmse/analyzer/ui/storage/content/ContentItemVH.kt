package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import coil.dispose
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.iconRes
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

        contentIcon.apply {
            if (content.lookup != null) {
                loadFilePreview(content.lookup)
            } else {
                dispose()
                setImageResource(content.type.iconRes)
            }
        }

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

        progress.apply {
            isGone = item.parent?.size == null
            progress = (((content.size ?: 0L) / (item.parent?.size ?: 1L).toDouble()) * 100).toInt()
        }

        root.apply {
            setOnClickListener { item.onItemClicked(item) }
            setOnLongClickListener {
                item.onItemLongPressed(item)
                true
            }
        }
    }

    data class Item(
        val parent: ContentItem?,
        val content: ContentItem,
        val onItemClicked: (Item) -> Unit,
        val onItemLongPressed: (Item) -> Unit,
    ) : ContentAdapter.Item {

        override val stableId: Long = content.path.hashCode().toLong()
    }

}