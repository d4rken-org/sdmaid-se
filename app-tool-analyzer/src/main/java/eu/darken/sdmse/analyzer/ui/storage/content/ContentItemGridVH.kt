package eu.darken.sdmse.analyzer.ui.storage.content

import android.graphics.Bitmap
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import coil.dispose
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.analyzer.databinding.AnalyzerContentItemGridVhBinding


class ContentItemGridVH(parent: ViewGroup) :
    ContentAdapter.BaseVH<ContentItemGridVH.Item, AnalyzerContentItemGridVhBinding>(
        R.layout.analyzer_content_item_grid_vh,
        parent
    ) {
    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { AnalyzerContentItemGridVhBinding.bind(itemView) }

    override val onBindData: AnalyzerContentItemGridVhBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val content = item.content

        previewImage.apply {
            val lookup = content.lookup
            if (lookup != null) {
                loadFilePreview(lookup) {
                    // Exception java.lang.IllegalArgumentException: Software rendering doesn't support hardware bitmaps
                    bitmapConfig(Bitmap.Config.ARGB_8888)
                }
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

        secondary.text = when {
            content.inaccessible -> content.size?.let {
                Formatter.formatShortFileSize(context, it)
            } ?: "?"

            content.type == FileType.DIRECTORY -> content.size?.let {
                val sizeFormatted = Formatter.formatShortFileSize(context, it)
                val itemsFormatted = getQuantityString(
                    eu.darken.sdmse.common.R.plurals.result_x_items,
                    content.children.size
                )
                "$sizeFormatted ($itemsFormatted)"
            } ?: "?"

            else -> content.size?.let {
                Formatter.formatShortFileSize(context, it)
            } ?: "?"
        }

        sizeBar.isVisible = item.sizeRatio != null
        if (item.sizeRatio != null) {
            val lp = sizeBar.layoutParams as ConstraintLayout.LayoutParams
            lp.matchConstraintPercentWidth = item.sizeRatio
            sizeBar.layoutParams = lp
        }

        root.setOnClickListener { item.onItemClicked() }
    }

    data class Item(
        val parent: ContentItem?,
        val content: ContentItem,
        val sizeRatio: Float?,
        val onItemClicked: () -> Unit,
    ) : ContentAdapter.Item {

        override val stableId: Long = content.path.hashCode().toLong()
        override val itemSelectionKey: String = content.path.path
    }

}
