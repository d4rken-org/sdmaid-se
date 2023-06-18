package eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.labelRes
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentElementFileBinding
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentElementsAdapter


class FilterContentElementFileVH(parent: ViewGroup) :
    FilterContentElementsAdapter.BaseVH<FilterContentElementFileVH.Item, SystemcleanerFiltercontentElementFileBinding>(
        R.layout.systemcleaner_filtercontent_element_file,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { SystemcleanerFiltercontentElementFileBinding.bind(itemView) }

    override val onBindData: SystemcleanerFiltercontentElementFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        icon.loadFilePreview(item.lookup)

        primary.text = item.lookup.userReadablePath.get(context)

        secondary.text = if (item.lookup.fileType == FileType.FILE) {
            Formatter.formatFileSize(context, item.lookup.size)
        } else {
            getString(item.lookup.fileType.labelRes)
        }

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val filterContent: FilterContent,
        val lookup: APathLookup<*>,
        val onItemClick: (Item) -> Unit,
    ) : FilterContentElementsAdapter.Item {

        override val itemSelectionKey: String
            get() = lookup.lookedUp.path

        override val stableId: Long = lookup.hashCode().toLong()
    }

}