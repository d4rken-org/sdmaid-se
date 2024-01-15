package eu.darken.sdmse.deduplicator.ui.list

import android.graphics.Bitmap
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.databinding.DeduplicatorListLinearItemBinding
import eu.darken.sdmse.deduplicator.core.Duplicate


class DeduplicatorListLinearVH(parent: ViewGroup) :
    DeduplicatorListAdapter.BaseVH<DeduplicatorListLinearVH.Item, DeduplicatorListLinearItemBinding>(
        R.layout.deduplicator_list_linear_item,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
        itemView.findViewById<View>(R.id.header_container).isActivated = selected
    }

    private val adapter = DeduplicatorListLinearSubAdapter()
    override val viewBinding = lazy {
        DeduplicatorListLinearItemBinding.bind(itemView).apply {
            clusterList.setupDefaults(adapter, dividers = false, fastscroll = false)
        }
    }

    override val onBindData: DeduplicatorListLinearItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val cluster = item.cluster

        previewImage.apply {
            loadFilePreview(cluster.previewFile) {
                // Exception java.lang.IllegalArgumentException: Software rendering doesn't support hardware bitmaps
                bitmapConfig(Bitmap.Config.ARGB_8888)
            }
            setOnClickListener { item.onPreviewClicked(item) }
        }

        primary.text = Formatter.formatShortFileSize(context, cluster.totalSize)
        secondary.text = context.getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, cluster.count)

        matchTypeChecksum.isVisible = item.cluster.types.contains(Duplicate.Type.CHECKSUM)
        matchTypePhash.isVisible = item.cluster.types.contains(Duplicate.Type.PHASH)

        val subItems = cluster.groups
            .flatMap { it.duplicates }
            .map { dupe ->
                DeduplicatorListLinearSubAdapter.DuplicateItemVH.Item(
                    cluster = cluster,
                    dupe = dupe,
                    onItemClicked = { item.onDupeClicked(it) },
                )
            }
        adapter.update(subItems)

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        override val cluster: Duplicate.Cluster,
        val onItemClicked: (Item) -> Unit,
        val onDupeClicked: (DeduplicatorListLinearSubAdapter.DuplicateItemVH.Item) -> Unit,
        val onPreviewClicked: (Item) -> Unit,
    ) : DeduplicatorListAdapter.Item {

        override val itemSelectionKey: String
            get() = cluster.identifier.toString()

        override val stableId: Long = cluster.identifier.hashCode().toLong()
    }


}