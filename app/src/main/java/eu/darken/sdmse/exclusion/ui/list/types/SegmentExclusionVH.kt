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

    override val viewBinding = lazy { ExclusionListItemSegmentBinding.bind(itemView) }

    override val onBindData: ExclusionListItemSegmentBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val excl = item.exclusion
        primary.text = excl.label.get(context)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val exclusion: SegmentExclusion,
        val onItemClick: (Item) -> Unit,
    ) : ExclusionListAdapter.Item {

        override val stableId: Long = exclusion.hashCode().toLong()
    }

}