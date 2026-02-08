package eu.darken.sdmse.squeezer.ui.list

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.squeezer.core.CompressibleImage
import javax.inject.Inject


class SqueezerListAdapter @Inject constructor() :
    ModularAdapter<SqueezerListAdapter.BaseVH<SqueezerListAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<SqueezerListAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod({ data }))
        addMod(TypedVHCreatorMod({ data[it] is SqueezerListGridVH.Item }) { SqueezerListGridVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SqueezerListLinearVH.Item }) { SqueezerListLinearVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup,
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem, SelectableItem {

        val image: CompressibleImage

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}
