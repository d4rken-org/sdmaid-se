package eu.darken.sdmse.systemcleaner.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.SystemcleanerListItemBinding
import eu.darken.sdmse.systemcleaner.core.FilterContent


class SystemCleanerListRowVH(parent: ViewGroup) :
    SystemCleanerListAdapter.BaseVH<SystemCleanerListRowVH.Item, SystemcleanerListItemBinding>(
        R.layout.systemcleaner_list_item,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { SystemcleanerListItemBinding.bind(itemView) }

    override val onBindData: SystemcleanerListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val content = item.content
        icon.setImageDrawable(content.icon.get(context))
        primary.text = content.label.get(context)
        secondary.text = content.description.get(context)

        items.text = getQuantityString(eu.darken.sdmse.common.R.plurals.result_x_items, content.items.size)
        size.text = Formatter.formatShortFileSize(context, content.size)

        root.setOnClickListener { item.onItemClicked(item) }
        detailsAction.setOnClickListener { item.onDetailsClicked(item) }
    }

    data class Item(
        override val content: FilterContent,
        val onItemClicked: (Item) -> Unit,
        val onDetailsClicked: (Item) -> Unit,
    ) : SystemCleanerListAdapter.Item, SelectableItem {
        override val itemSelectionKey: String = content.identifier
        override val stableId: Long = content.identifier.hashCode().toLong()
    }

}