package eu.darken.sdmse.corpsefinder.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.iconRes
import eu.darken.sdmse.corpsefinder.ui.labelRes
import eu.darken.sdmse.databinding.CorpsefinderListItemBinding


class CorpseRowVH(parent: ViewGroup) :
    CorpseListAdapter.BaseVH<CorpseRowVH.Item, CorpsefinderListItemBinding>(
        R.layout.corpsefinder_list_item,
        parent
    ) {

    override val viewBinding = lazy { CorpsefinderListItemBinding.bind(itemView) }

    override val onBindData: CorpsefinderListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val corpse = item.corpse

        icon.setImageResource(corpse.filterType.iconRes)
        primary.text = corpse.path.userReadableName.get(context)
        secondary.text = corpse.path.userReadablePath.get(context)

        when (corpse.riskLevel) {
            RiskLevel.NORMAL -> {
                tertiary.isVisible = false
            }
            RiskLevel.KEEPER -> {
                tertiary.isVisible = true
            }
            RiskLevel.COMMON -> {
                tertiary.isVisible = true
            }
        }

        areaInfo.text = getString(corpse.filterType.labelRes)
        size.text = StringBuilder().apply {
            if (corpse.content.isNotEmpty()) {
                append(getQuantityString(R.plurals.result_x_items, corpse.content.size))
                append(", ")
            }
            append(Formatter.formatShortFileSize(context, corpse.size))
        }

        root.setOnClickListener { item.onItemClicked(corpse) }
        detailsAction.setOnClickListener { item.onDetailsClicked(corpse) }
    }

    data class Item(
        val corpse: Corpse,
        val onItemClicked: (Corpse) -> Unit,
        val onDetailsClicked: (Corpse) -> Unit,
    ) : CorpseListAdapter.Item {

        override val stableId: Long = corpse.path.hashCode().toLong()
    }

}