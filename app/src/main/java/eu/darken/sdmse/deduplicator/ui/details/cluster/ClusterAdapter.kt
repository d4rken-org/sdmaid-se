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
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ClusterHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.DirectoryHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.PHashGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.PHashGroupHeaderVH
import javax.inject.Inject


class ClusterAdapter @Inject constructor() :
    ModularAdapter<ClusterAdapter.BaseVH<ClusterAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<ClusterAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is ClusterHeaderVH.Item }) { ClusterHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is DirectoryHeaderVH.Item }) { DirectoryHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ChecksumGroupHeaderVH.Item }) { ChecksumGroupHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ChecksumGroupFileVH.Item }) { ChecksumGroupFileVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is PHashGroupHeaderVH.Item }) { PHashGroupHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is PHashGroupFileVH.Item }) { PHashGroupFileVH(it) })
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

    interface ClusterItem : Item {
        val cluster: Duplicate.Cluster
        val identifier: Duplicate.Cluster.Id
            get() = cluster.identifier

        interface VH
    }

    interface GroupItem : Item {
        val group: Duplicate.Group
        val identifier: Duplicate.Group.Id
            get() = group.identifier

        interface VH
    }

    interface DuplicateItem : Item {
        val duplicate: Duplicate
        val identifier: Duplicate.Id
            get() = duplicate.identifier
        val path: APath
            get() = duplicate.path

        interface VH
    }

    interface DirectoryItem : Item {
        val directory: DirectoryGroup

        interface VH
    }
}