package eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentElementHeaderBinding
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.filter.getDescription
import eu.darken.sdmse.systemcleaner.core.filter.getIcon
import eu.darken.sdmse.systemcleaner.core.filter.getLabel
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentElementsAdapter


class FilterContentElementHeaderVH(parent: ViewGroup) :
    FilterContentElementsAdapter.BaseVH<FilterContentElementHeaderVH.Item, SystemcleanerFiltercontentElementHeaderBinding>(
        R.layout.systemcleaner_filtercontent_element_header,
        parent
    ) {

    override val viewBinding = lazy { SystemcleanerFiltercontentElementHeaderBinding.bind(itemView) }

    override val onBindData: SystemcleanerFiltercontentElementHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val fc = item.filterContent
        icon.setImageDrawable(fc.identifier.getIcon(context))
        typeValue.text = fc.identifier.getLabel(context)
        countValue.text = context.getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, fc.items.size)
        sizeVaule.text = Formatter.formatFileSize(context, fc.size)
        descriptionValue.text = fc.identifier.getDescription(context)

        deleteAction.setOnClickListener { item.onDeleteAllClicked(item) }
        excludeAction.setOnClickListener { item.onExcludeClicked(item) }
    }

    data class Item(
        val filterContent: FilterContent,
        val onDeleteAllClicked: (Item) -> Unit,
        val onExcludeClicked: (Item) -> Unit,
    ) : FilterContentElementsAdapter.Item {
        override val itemSelectionKey: String? = null
        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}