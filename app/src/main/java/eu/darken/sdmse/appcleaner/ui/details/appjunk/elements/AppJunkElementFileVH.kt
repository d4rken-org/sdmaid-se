package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.labelRes
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.AppcleanerAppjunkElementFileBinding
import kotlin.reflect.KClass


class AppJunkElementFileVH(parent: ViewGroup) :
    AppJunkElementsAdapter.BaseVH<AppJunkElementFileVH.Item, AppcleanerAppjunkElementFileBinding>(
        R.layout.appcleaner_appjunk_element_file,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { AppcleanerAppjunkElementFileBinding.bind(itemView) }

    override val onBindData: AppcleanerAppjunkElementFileBinding.(
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
        val appJunk: AppJunk,
        val category: KClass<out ExpendablesFilter>,
        val lookup: APathLookup<*>,
        val onItemClick: (Item) -> Unit,
    ) : AppJunkElementsAdapter.Item {

        override val itemSelectionKey: String = lookup.path
        override val stableId: Long = lookup.hashCode().toLong()
    }

}