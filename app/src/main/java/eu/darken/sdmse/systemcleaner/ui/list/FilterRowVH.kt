package eu.darken.sdmse.systemcleaner.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SystemcleanerListItemBinding
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.filter.getDescription
import eu.darken.sdmse.systemcleaner.core.filter.getIcon
import eu.darken.sdmse.systemcleaner.core.filter.getLabel


class FilterRowVH(parent: ViewGroup) :
    FilterListAdapter.BaseVH<FilterRowVH.Item, SystemcleanerListItemBinding>(
        R.layout.systemcleaner_list_item,
        parent
    ) {

    override val viewBinding = lazy { SystemcleanerListItemBinding.bind(itemView) }

    override val onBindData: SystemcleanerListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val content = item.content
        icon.setImageDrawable(content.filterIdentifier.getIcon(context))
        primary.text = content.filterIdentifier.getLabel(context)
        secondary.text = content.filterIdentifier.getDescription(context)

        items.text = getQuantityString(R.plurals.result_x_items, content.items.size)
        size.text = Formatter.formatShortFileSize(context, content.size)

        root.setOnClickListener { item.onItemClicked(content) }
        detailsAction.setOnClickListener { item.onDetailsClicked(content) }
    }

    data class Item(
        val content: FilterContent,
        val onItemClicked: (FilterContent) -> Unit,
        val onDetailsClicked: (FilterContent) -> Unit,
    ) : FilterListAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}