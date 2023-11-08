package eu.darken.sdmse.deduplicator.ui.details.cluster.elements

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.databinding.DeduplicatorClusterElementPhashgroupHeaderBinding
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterAdapter
import kotlin.math.roundToLong


class PHashGroupHeaderVH(parent: ViewGroup) :
    ClusterAdapter.BaseVH<PHashGroupHeaderVH.Item, DeduplicatorClusterElementPhashgroupHeaderBinding>(
        R.layout.deduplicator_cluster_element_phashgroup_header,
        parent
    ), ClusterAdapter.ClusterItem.VH, ModularAdapter.Module.RecyclerViewLifecycle {

    override val viewBinding = lazy { DeduplicatorClusterElementPhashgroupHeaderBinding.bind(itemView) }
    private var handler: Handler? = null
    private var callback: Runnable? = null

    override val onBindData: DeduplicatorClusterElementPhashgroupHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val group = item.group

        if (handler == null) {
            handler = Handler(Looper.getMainLooper())
        } else {
            callback?.let { handler!!.removeCallbacks(it) }
        }

        var imageIndex = 0
        val imageChanger = {
            val previews = group.duplicates.map { it.lookup }
            if (previews.isNotEmpty()) {
                val preview = previews[imageIndex]
                previewImage.loadFilePreview(preview)
                @SuppressLint("SetTextI18n")
                previewPath.text = preview.userReadablePath.get(context)
                imageIndex = (imageIndex + 1) % previews.size
            }
        }
        previewImage.post(object : Runnable {
            override fun run() {
                imageChanger()
                previewImage.postDelayed(this, 3000)
            }
        })

        countValue.text = context.getQuantityString2(R.plurals.deduplicator_result_x_duplicates, group.count)
        sizeValue.text = Formatter.formatFileSize(context, group.averageSize.roundToLong())

        viewAction.setOnClickListener { item.onViewActionClick(item) }
        root.setOnClickListener { item.onItemClick(item) }
    }

    private fun cleanup() {
        handler?.apply {
            callback?.let {
                removeCallbacks(it)
                callback = null
            }
            removeCallbacksAndMessages(null)
            handler = null
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {

    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        cleanup()
    }

    data class Item(
        override val group: PHashDuplicate.Group,
        val onItemClick: (Item) -> Unit,
        val onViewActionClick: (Item) -> Unit,
    ) : ClusterAdapter.Item, SelectableItem, ClusterAdapter.GroupItem {

        override val itemSelectionKey: String?
            get() = null
        override val stableId: Long = group.identifier.hashCode().toLong()
    }

}