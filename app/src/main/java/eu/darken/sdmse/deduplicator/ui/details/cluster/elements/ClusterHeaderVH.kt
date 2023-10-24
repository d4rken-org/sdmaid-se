package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.databinding.DeduplicatorClusterElementHeaderBinding
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter


class ClusterHeaderVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<ClusterHeaderVH.Item, DeduplicatorClusterElementHeaderBinding>(
        R.layout.deduplicator_cluster_element_header,
        parent
    ), ClusterAdapter.HeaderVH {

    override val viewBinding = lazy { DeduplicatorClusterElementHeaderBinding.bind(itemView) }

    override val onBindData: DeduplicatorClusterElementHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val cluster = item.cluster

        methodChecksumCount.text = getString(
            R.string.deduplicator_result_x_duplicates,
            cluster.groups.filterIsInstance<ChecksumDuplicate.Group>().sumOf { it.count }
        )

        methodPhashValue.text = getString(
            R.string.deduplicator_result_x_duplicates,
            cluster.groups.filterIsInstance<PHashDuplicate.Group>().sumOf { it.count }
        )

        sizeValue.text = Formatter.formatShortFileSize(context, cluster.totalSize)

        deleteAction.setOnClickListener { item.onDeleteAllClicked(item) }
        excludeAction.setOnClickListener { item.onExcludeClicked(item) }
    }

    data class Item(
        val cluster: Duplicate.Cluster,
        val onDeleteAllClicked: (Item) -> Unit,
        val onExcludeClicked: (Item) -> Unit,
    ) : ClusterAdapter.Item, SelectableItem {

        override val itemSelectionKey: String? = null

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}