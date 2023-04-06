package eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentElementFileBinding
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentElementsAdapter


class FilterContentElementFileVH(parent: ViewGroup) :
    FilterContentElementsAdapter.BaseVH<FilterContentElementFileVH.Item, SystemcleanerFiltercontentElementFileBinding>(
        R.layout.systemcleaner_filtercontent_element_file,
        parent
    ) {

    override val viewBinding = lazy { SystemcleanerFiltercontentElementFileBinding.bind(itemView) }

    override val onBindData: SystemcleanerFiltercontentElementFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        when (item.lookup.fileType) {
            FileType.DIRECTORY -> R.drawable.ic_folder
            FileType.SYMBOLIC_LINK -> R.drawable.ic_file_link
            FileType.FILE -> R.drawable.ic_file
        }.run { icon.setImageResource(this) }

        primary.text = item.lookup.userReadablePath.get(context)

        secondary.text = when (item.lookup.fileType) {
            FileType.DIRECTORY -> getString(R.string.file_type_directory)
            FileType.SYMBOLIC_LINK -> getString(R.string.file_type_symbolic_link)
            FileType.FILE -> Formatter.formatFileSize(context, item.lookup.size)
        }

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val filterContent: FilterContent,
        val lookup: APathLookup<*>,
        val onItemClick: (Item) -> Unit,
    ) : FilterContentElementsAdapter.Item {

        override val stableId: Long = lookup.hashCode().toLong()
    }

}