package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.deduplicator.R
import androidx.core.view.isVisible
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.deduplicator.databinding.DeduplicatorClusterElementChecksumgroupHeaderBinding
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter
import kotlin.math.roundToLong


class ChecksumGroupHeaderVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<ChecksumGroupHeaderVH.Item, DeduplicatorClusterElementChecksumgroupHeaderBinding>(
        R.layout.deduplicator_cluster_element_checksumgroup_header,
        parent
    ), ClusterAdapter.GroupItem.VH {

    override val viewBinding = lazy { DeduplicatorClusterElementChecksumgroupHeaderBinding.bind(itemView) }

    override val onBindData: DeduplicatorClusterElementChecksumgroupHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val group = item.group

        previewImage.loadFilePreview(group.preview)

        countValue.text = context.getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_files, group.count)
        sizeValue.text = Formatter.formatFileSize(context, group.averageSize.roundToLong())

        deleteIcon.isVisible = item.willBeDeleted

        headerContainer.setOnClickListener { item.onItemClick(item) }
        footerContainer.setOnClickListener { item.onItemClick(item) }
        root.setOnClickListener { item.onViewActionClick(item) }
    }

    data class Item(
        override val group: ChecksumDuplicate.Group,
        val willBeDeleted: Boolean = false,
        val onItemClick: (Item) -> Unit,
        val onViewActionClick: (Item) -> Unit,
    ) : ClusterAdapter.GroupItem, SelectableItem {

        override val itemSelectionKey: String?
            get() = null

        override val stableId: Long = group.identifier.hashCode().toLong()
    }

}