package eu.darken.sdmse.corpsefinder.ui.details.corpse.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseElementsAdapter
import eu.darken.sdmse.databinding.CorpsefinderCorpseElementFileBinding


class CorpseElementFileVH(parent: ViewGroup) :
    CorpseElementsAdapter.BaseVH<CorpseElementFileVH.Item, CorpsefinderCorpseElementFileBinding>(
        R.layout.corpsefinder_corpse_element_file,
        parent
    ) {

    override val viewBinding = lazy { CorpsefinderCorpseElementFileBinding.bind(itemView) }

    override val onBindData: CorpsefinderCorpseElementFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        icon.setImageResource(item.lookup.fileType.iconRes)

        val prefixFree = item.lookup.lookedUp.removePrefix(item.corpse.path)
        primary.text = prefixFree.joinSegments("/")

        secondary.text = if (item.lookup.fileType == FileType.FILE) {
            Formatter.formatFileSize(context, item.lookup.size)
        } else {
            getString(item.lookup.fileType.labelRes)
        }

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val corpse: Corpse,
        val lookup: APathLookup<*>,
        val onItemClick: (Item) -> Unit,
    ) : CorpseElementsAdapter.Item {

        override val stableId: Long = lookup.hashCode().toLong()
    }

}