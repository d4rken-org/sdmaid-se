package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.databinding.DeduplicatorClusterElementDirectoryHeaderBinding
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter
import eu.darken.sdmse.deduplicator.ui.details.cluster.DirectoryGroup


class DirectoryHeaderVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<DirectoryHeaderVH.Item, DeduplicatorClusterElementDirectoryHeaderBinding>(
        R.layout.deduplicator_cluster_element_directory_header,
        parent
    ), ClusterAdapter.DirectoryItem.VH {

    override val viewBinding = lazy { DeduplicatorClusterElementDirectoryHeaderBinding.bind(itemView) }

    override val onBindData: DeduplicatorClusterElementDirectoryHeaderBinding.(
        item: Item,
        payloads: List<Any>,
    ) -> Unit = binding { item ->
        val group = item.directoryGroup

        primary.text = group.parentSegments.joinSegments()

        secondary.apply {
            text = Formatter.formatFileSize(context, group.totalSize)
            append(
                " (${
                    context.getQuantityString2(
                        eu.darken.sdmse.common.R.plurals.result_x_items,
                        group.count
                    )
                })"
            )
        }

        collapseAction.apply {
            setIconResource(
                if (item.isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
            setOnClickListener { item.onCollapseToggle() }
        }

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val directoryGroup: DirectoryGroup,
        val onItemClick: (Item) -> Unit,
        val isCollapsed: Boolean,
        val onCollapseToggle: () -> Unit,
    ) : ClusterAdapter.Item, ClusterAdapter.DirectoryItem, SelectableItem {

        override val directory: DirectoryGroup
            get() = directoryGroup

        override val itemSelectionKey: String? = null

        override val stableId: Long = directoryGroup.identifier.hashCode().toLong()
    }
}
