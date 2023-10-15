package eu.darken.sdmse.deduplicator.ui.list.types

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.DeduplicatorGroupListHashItemBinding
import eu.darken.sdmse.deduplicator.core.types.Duplicate
import eu.darken.sdmse.deduplicator.ui.list.DuplicateGroupListAdapter


class HashGroupRowVH(parent: ViewGroup) :
    DuplicateGroupListAdapter.BaseVH<HashGroupRowVH.Item, DeduplicatorGroupListHashItemBinding>(
        R.layout.deduplicator_group_list_hash_item,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { DeduplicatorGroupListHashItemBinding.bind(itemView) }

    override val onBindData: DeduplicatorGroupListHashItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val group = item.group

        primary.text = Formatter.formatShortFileSize(context, group.size)
        secondary.text = getQuantityString(eu.darken.sdmse.common.R.plurals.result_x_items, group.count)

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        override val group: Duplicate.Group,
        val onItemClicked: (Item) -> Unit,
    ) : DuplicateGroupListAdapter.Item {

        override val itemSelectionKey: String
            get() = group.identifier.toString()

        override val stableId: Long = group.identifier.hashCode().toLong()
    }

}