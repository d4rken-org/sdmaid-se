package eu.darken.sdmse.deduplicator.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.DeduplicatorListGridItemBinding
import eu.darken.sdmse.deduplicator.core.types.Duplicate


class DeduplicatorListGridVH(parent: ViewGroup) :
    DeduplicatorListAdapter.BaseVH<DeduplicatorListGridVH.Item, DeduplicatorListGridItemBinding>(
        R.layout.deduplicator_list_grid_item,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { DeduplicatorListGridItemBinding.bind(itemView) }

    override val onBindData: DeduplicatorListGridItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val cluster = item.cluster

        previewImage.loadFilePreview(cluster.previewFile)

        primary.text = Formatter.formatShortFileSize(context, cluster.totalSize)
        secondary.text = getString(R.string.deduplicator_result_x_duplicates, cluster.count)

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        override val cluster: Duplicate.Cluster,
        val onItemClicked: (Item) -> Unit,
    ) : DeduplicatorListAdapter.Item {

        override val itemSelectionKey: String
            get() = cluster.identifier.toString()

        override val stableId: Long = cluster.identifier.hashCode().toLong()
    }

}