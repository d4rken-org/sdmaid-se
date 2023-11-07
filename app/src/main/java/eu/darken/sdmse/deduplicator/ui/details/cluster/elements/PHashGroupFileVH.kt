package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.DeduplicatorClusterElementPhashgroupFileBinding
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter


class PHashGroupFileVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<PHashGroupFileVH.Item, DeduplicatorClusterElementPhashgroupFileBinding>(
        R.layout.deduplicator_cluster_element_phashgroup_file,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { DeduplicatorClusterElementPhashgroupFileBinding.bind(itemView) }

    override val onBindData: DeduplicatorClusterElementPhashgroupFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val dupe = item.duplicate

        previewImage.loadFilePreview(dupe.lookup)
        primary.text = dupe.lookup.userReadablePath.get(context)
        secondary.text = String.format("%.2f%%", dupe.similarity * 100)
        sizeValue.text = Formatter.formatShortFileSize(context, dupe.size)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        override val duplicate: PHashDuplicate,
        val onItemClick: (Item) -> Unit,
    ) : ClusterAdapter.Item, ClusterAdapter.DuplicateItem, SelectableItem {

        override val itemSelectionKey: String
            get() = duplicate.lookup.toString()

        override val stableId: Long = itemSelectionKey.hashCode().toLong()
    }

}