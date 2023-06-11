package eu.darken.sdmse.exclusion.ui.list.types

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.ExclusionListItemSegmentBinding
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import eu.darken.sdmse.exclusion.ui.list.ExclusionListAdapter


class SegmentExclusionVH(parent: ViewGroup) :
    ExclusionListAdapter.BaseVH<SegmentExclusionVH.Item, ExclusionListItemSegmentBinding>(
        R.layout.exclusion_list_item_segment,
        parent
    ) {
    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { ExclusionListItemSegmentBinding.bind(itemView) }

    override val onBindData: ExclusionListItemSegmentBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val excl = item.exclusion
        primary.text = excl.label.get(context)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        override val exclusion: SegmentExclusion,
        val onItemClick: (Item) -> Unit,
    ) : ExclusionListAdapter.Item {
        override val stableId: Long = exclusion.hashCode().toLong()
        override val itemSelectionKey: String = exclusion.id
    }

}