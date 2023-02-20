package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcleanerAppjunkElementFileBinding
import kotlin.reflect.KClass


class AppJunkElementFileVH(parent: ViewGroup) :
    AppJunkElementsAdapter.BaseVH<AppJunkElementFileVH.Item, AppcleanerAppjunkElementFileBinding>(
        R.layout.appcleaner_appjunk_element_file,
        parent
    ) {

    override val viewBinding = lazy { AppcleanerAppjunkElementFileBinding.bind(itemView) }

    override val onBindData: AppcleanerAppjunkElementFileBinding.(
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
        val appJunk: AppJunk,
        val category: KClass<out ExpendablesFilter>,
        val lookup: APathLookup<*>,
        val onItemClick: (Item) -> Unit,
    ) : AppJunkElementsAdapter.Item {

        override val stableId: Long = lookup.hashCode().toLong()
    }

}