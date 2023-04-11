package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.files.labelRes
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

        icon.setImageResource(item.lookup.fileType.iconRes)

        primary.text = item.lookup.userReadablePath.get(context)

        secondary.text = if (item.lookup.fileType == FileType.FILE) {
            Formatter.formatFileSize(context, item.lookup.size)
        } else {
            getString(item.lookup.fileType.labelRes)
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