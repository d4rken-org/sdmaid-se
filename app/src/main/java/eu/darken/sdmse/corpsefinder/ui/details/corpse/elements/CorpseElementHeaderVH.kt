package eu.darken.sdmse.corpsefinder.ui.details.corpse.elements

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseElementsAdapter
import eu.darken.sdmse.corpsefinder.ui.iconRes
import eu.darken.sdmse.corpsefinder.ui.labelRes
import eu.darken.sdmse.databinding.CorpsefinderCorpseElementHeaderBinding


class CorpseElementHeaderVH(parent: ViewGroup) :
    CorpseElementsAdapter.BaseVH<CorpseElementHeaderVH.Item, CorpsefinderCorpseElementHeaderBinding>(
        R.layout.corpsefinder_corpse_element_header,
        parent
    ) {

    override val viewBinding = lazy { CorpsefinderCorpseElementHeaderBinding.bind(itemView) }

    override val onBindData: CorpsefinderCorpseElementHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val corpse = item.corpse
        pathValue.text = corpse.lookup.userReadablePath.get(context)
        typeIcon.setImageResource(corpse.filterType.iconRes)
        typeValue.text = getString(corpse.filterType.labelRes)

        sizeIcon.setImageResource(
            when {
                corpse.lookup.fileType == FileType.DIRECTORY -> R.drawable.ic_folder
                corpse.lookup.fileType == FileType.FILE -> R.drawable.ic_file
                else -> R.drawable.file_question
            }
        )
        sizeVaule.text = Formatter.formatFileSize(context, corpse.size)

        ownersValue.text = if (corpse.ownerInfo.owners.isNotEmpty()) {
            corpse.ownerInfo.owners.joinToString("\n") { it.pkgId.name }
        } else {
            getString(R.string.corpsefinder_owner_unknown)
        }

        hintsLabel.isGone = corpse.riskLevel == RiskLevel.NORMAL
        hintsValue.isGone = corpse.riskLevel == RiskLevel.NORMAL

        when (corpse.riskLevel) {
            RiskLevel.NORMAL -> hintsValue.apply {
                text = ""
                setTextColor(getColorForAttr(com.google.android.material.R.attr.colorPrimary))
            }

            RiskLevel.KEEPER -> hintsValue.apply {
                text = getString(R.string.corpsefinder_corpse_hint_keeper)
                setTextColor(getColorForAttr(com.google.android.material.R.attr.colorSecondary))
            }

            RiskLevel.COMMON -> hintsValue.apply {
                text = getString(R.string.corpsefinder_corpse_hint_common)
                setTextColor(getColorForAttr(com.google.android.material.R.attr.colorTertiary))
            }
        }


        deleteAction.setOnClickListener { item.onDeleteAllClicked(item) }
        excludeAction.setOnClickListener { item.onExcludeClicked(item) }
    }

    data class Item(
        val corpse: Corpse,
        val onDeleteAllClicked: (Item) -> Unit,
        val onExcludeClicked: (Item) -> Unit,
    ) : CorpseElementsAdapter.Item, SelectableItem {

        override val itemSelectionKey: String? = null

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}