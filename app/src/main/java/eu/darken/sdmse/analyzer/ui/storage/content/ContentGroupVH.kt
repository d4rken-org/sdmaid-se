package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerContentGroupVhBinding


class ContentGroupVH(parent: ViewGroup) :
    ContentAdapter.BaseVH<ContentGroupVH.Item, AnalyzerContentGroupVhBinding>(
        R.layout.analyzer_content_group_vh,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerContentGroupVhBinding.bind(itemView) }

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val onBindData: AnalyzerContentGroupVhBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val content = item.contentGroup

        primary.text = content.label?.get(context)
        secondary.text = Formatter.formatShortFileSize(context, content.groupSize)

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val category: MediaCategory,
        val contentGroup: ContentGroup,
        val onItemClicked: (Item) -> Unit,
    ) : ContentAdapter.Item {

        override val stableId: Long = contentGroup.id.hashCode().toLong()
        override val itemSelectionKey: String = contentGroup.id.value
    }

}