package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcleanerAppjunkElementInaccessibleBinding


class AppJunkElementInaccessibleVH(parent: ViewGroup) :
    AppJunkElementsAdapter.BaseVH<AppJunkElementInaccessibleVH.Item, AppcleanerAppjunkElementInaccessibleBinding>(
        R.layout.appcleaner_appjunk_element_inaccessible,
        parent
    ) {

    override val viewBinding = lazy { AppcleanerAppjunkElementInaccessibleBinding.bind(itemView) }

    override val onBindData: AppcleanerAppjunkElementInaccessibleBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        size.text = Formatter.formatFileSize(context, item.inaccessibleCache.privateCacheSize)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val appJunk: AppJunk,
        val inaccessibleCache: InaccessibleCache,
        val onItemClick: (Item) -> Unit,
    ) : AppJunkElementsAdapter.Item {

        override val itemSelectionKey: String? = null
        override val stableId: Long = Item::class.java.hashCode().toLong()
    }

}