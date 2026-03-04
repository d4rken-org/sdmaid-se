package eu.darken.sdmse.corpsefinder.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.R
import eu.darken.sdmse.corpsefinder.databinding.CorpsefinderListItemBinding
import eu.darken.sdmse.corpsefinder.ui.iconRes
import eu.darken.sdmse.corpsefinder.ui.labelRes


class CorpseFinderListRowVH(parent: ViewGroup) :
    CorpseFinderListAdapter.BaseVH<CorpseFinderListRowVH.Item, CorpsefinderListItemBinding>(
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
            RiskLevel.NORMAL -> tertiary.apply {
                isVisible = false
                setTextColor(getColorForAttr(androidx.appcompat.R.attr.colorPrimary))
            }

            RiskLevel.KEEPER -> tertiary.apply {
                text = getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_corpse_hint_keeper)
                isVisible = true
                setTextColor(getColorForAttr(com.google.android.material.R.attr.colorSecondary))
            }

            RiskLevel.COMMON -> tertiary.apply {
                text = getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_corpse_hint_common)
                isVisible = true
                setTextColor(getColorForAttr(com.google.android.material.R.attr.colorTertiary))
            }
        }

        areaInfo.text = getString(corpse.filterType.labelRes)
        sizeIcon.setImageResource(corpse.lookup.fileType.iconRes)
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
    ) : CorpseFinderListAdapter.Item {

        override val itemSelectionKey: String
            get() = corpse.identifier.toString()

        override val stableId: Long = corpse.identifier.hashCode().toLong()
    }

}