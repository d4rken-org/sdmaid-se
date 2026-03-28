package eu.darken.sdmse.analyzer.ui.storage.content

import android.view.ViewGroup
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.analyzer.databinding.AnalyzerContentInfoVhBinding


class ContentInfoVH(parent: ViewGroup) :
    ContentAdapter.BaseVH<ContentInfoVH.Item, AnalyzerContentInfoVhBinding>(
        R.layout.analyzer_content_info_vh,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerContentInfoVhBinding.bind(itemView) }

    override val itemSelectionKey: String? = null

    override fun updatedSelectionState(selected: Boolean) {
        // Not selectable
    }

    override val onBindData: AnalyzerContentInfoVhBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        infoText.setText(item.messageRes)
    }

    data class Item(
        val messageRes: Int,
    ) : ContentAdapter.Item {
        override val stableId: Long = "content-info".hashCode().toLong()
        override val itemSelectionKey: String? = null
    }
}
