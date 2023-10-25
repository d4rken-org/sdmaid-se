package eu.darken.sdmse.deduplicator.ui.details.cluster

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ClusterHeaderVH
import javax.inject.Inject


class ClusterAdapter @Inject constructor() :
    ModularAdapter<ClusterAdapter.BaseVH<ClusterAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<ClusterAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is ClusterHeaderVH.Item }) { ClusterHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ChecksumGroupHeaderVH.Item }) { ChecksumGroupHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ChecksumGroupFileVH.Item }) { ChecksumGroupFileVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem, SelectableItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }

    interface HeaderVH

    interface FileItem {
        val path: APath
    }
}