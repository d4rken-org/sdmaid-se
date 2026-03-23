package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.transform.RoundedCornersTransformation
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.replaceLast
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.databinding.DeduplicatorClusterElementMediagroupFileBinding
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter


class MediaGroupFileVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<MediaGroupFileVH.Item, DeduplicatorClusterElementMediagroupFileBinding>(
        R.layout.deduplicator_cluster_element_mediagroup_file,
        parent
    ), ClusterAdapter.DuplicateItem.VH, SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { DeduplicatorClusterElementMediagroupFileBinding.bind(itemView) }

    override val onBindData: DeduplicatorClusterElementMediagroupFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        val dupe = item.duplicate

        previewImage.loadFilePreview(dupe.lookup) {
            transformations(RoundedCornersTransformation(36F))
        }
        previewImage.setOnClickListener { item.onPreviewClick(item) }

        val fileName = dupe.path.userReadableName.get(context)
        name.text = fileName
        path.text = dupe.path.userReadablePath.get(context).replaceLast(fileName, "")

        val matchType = when {
            dupe.audioHash != null && dupe.frameHashes.isNotEmpty() -> context.getString(R.string.deduplicator_media_match_audio_visual)
            dupe.audioHash != null -> context.getString(R.string.deduplicator_media_match_audio)
            dupe.frameHashes.isNotEmpty() -> context.getString(R.string.deduplicator_media_match_visual)
            else -> ""
        }
        secondary.text = "${String.format("%.2f%%", dupe.similarity * 100)} ($matchType)"
        sizeValue.text = Formatter.formatShortFileSize(context, dupe.size)

        deleteIcon.isVisible = item.willBeDeleted

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        override val duplicate: MediaDuplicate,
        val willBeDeleted: Boolean = false,
        val onItemClick: (Item) -> Unit,
        val onPreviewClick: (Item) -> Unit,
    ) : ClusterAdapter.DuplicateItem, SelectableItem {

        override val itemSelectionKey: String
            get() = duplicate.lookup.toString()

        override val stableId: Long = itemSelectionKey.hashCode().toLong()
    }

}
