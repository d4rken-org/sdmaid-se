package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.DeduplicatorClusterElementChecksumgroupFileBinding
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter


class ChecksumGroupFileVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<ChecksumGroupFileVH.Item, DeduplicatorClusterElementChecksumgroupFileBinding>(
        R.layout.deduplicator_cluster_element_checksumgroup_file,
        parent
    ), SelectableVH, ClusterAdapter.DuplicateItem.VH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { DeduplicatorClusterElementChecksumgroupFileBinding.bind(itemView) }

    override val onBindData: DeduplicatorClusterElementChecksumgroupFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val duplicate = item.duplicate

        primary.text = duplicate.lookup.userReadablePath.get(context)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        override val duplicate: ChecksumDuplicate,
        val onItemClick: (Item) -> Unit,
    ) : ClusterAdapter.DuplicateItem, SelectableItem {

        override val itemSelectionKey: String
            get() = duplicate.lookup.toString()

        override val stableId: Long = itemSelectionKey.hashCode().toLong()
    }

}