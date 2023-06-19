package eu.darken.sdmse.corpsefinder.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.iconRes
import eu.darken.sdmse.corpsefinder.ui.labelRes
import eu.darken.sdmse.databinding.CorpsefinderListItemBinding


class CorpseRowVH(parent: ViewGroup) :
    CorpseListAdapter.BaseVH<CorpseRowVH.Item, CorpsefinderListItemBinding>(
        R.layout.corpsefinder_list_item,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { CorpsefinderListItemBinding.bind(itemView) }

    override val onBindData: CorpsefinderListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val corpse = item.corpse

        icon.setImageResource(corpse.filterType.iconRes)
        primary.text = corpse.lookup.userReadableName.get(context)
        secondary.text = corpse.lookup.userReadablePath.get(context).removeSuffix(primary.text)

        when (corpse.riskLevel) {
            RiskLevel.NORMAL -> {
                tertiary.isVisible = false
            }

            RiskLevel.KEEPER -> {
                tertiary.text = getString(R.string.corpsefinder_corpse_hint_keeper)
                tertiary.isVisible = true
            }

            RiskLevel.COMMON -> {
                tertiary.text = getString(R.string.corpsefinder_corpse_hint_common)
                tertiary.isVisible = true
            }
        }

        areaInfo.text = getString(corpse.filterType.labelRes)
        size.text = StringBuilder().apply {
            if (corpse.content.isNotEmpty()) {
                append(getQuantityString(eu.darken.sdmse.common.R.plurals.result_x_items, corpse.content.size))
                append(", ")
            }
            append(Formatter.formatShortFileSize(context, corpse.size))
        }

        root.setOnClickListener { item.onItemClicked(item) }
        detailsAction.setOnClickListener { item.onDetailsClicked(item) }
    }

    data class Item(
        override val corpse: Corpse,
        val onItemClicked: (Item) -> Unit,
        val onDetailsClicked: (Item) -> Unit,
    ) : CorpseListAdapter.Item {

        override val itemSelectionKey: String
            get() = corpse.identifier.toString()

        override val stableId: Long = corpse.identifier.hashCode().toLong()
    }

}