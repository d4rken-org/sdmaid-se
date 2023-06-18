package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.ui.descriptionRes
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.appcleaner.ui.iconsRes
import eu.darken.sdmse.appcleaner.ui.labelRes
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AppcleanerAppjunkElementFileCategoryBinding
import kotlin.reflect.KClass


class AppJunkElementFileCategoryVH(parent: ViewGroup) :
    AppJunkElementsAdapter.BaseVH<AppJunkElementFileCategoryVH.Item, AppcleanerAppjunkElementFileCategoryBinding>(
        R.layout.appcleaner_appjunk_element_file_category,
        parent
    ) {

    override val viewBinding = lazy { AppcleanerAppjunkElementFileCategoryBinding.bind(itemView) }

    override val onBindData: AppcleanerAppjunkElementFileCategoryBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        icon.setImageResource(item.category.iconsRes)
        primary.text = getString(item.category.labelRes)
        secondary.apply {
            text = Formatter.formatFileSize(context, item.paths.sumOf { it.size })
            append(" (${context.getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, item.paths.size)})")
        }
        description.text = getString(item.category.descriptionRes)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val appJunk: AppJunk,
        val category: KClass<out ExpendablesFilter>,
        val paths: Collection<APathLookup<*>>,
        val onItemClick: (Item) -> Unit,
    ) : AppJunkElementsAdapter.Item {

        override val itemSelectionKey: String? = null
        override val stableId: Long = category.hashCode().toLong()
    }

}