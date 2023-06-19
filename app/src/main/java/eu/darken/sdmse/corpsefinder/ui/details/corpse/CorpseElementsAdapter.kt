package eu.darken.sdmse.corpsefinder.ui.details.corpse

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
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementFileVH
import eu.darken.sdmse.corpsefinder.ui.details.corpse.elements.CorpseElementHeaderVH
import javax.inject.Inject


class CorpseElementsAdapter @Inject constructor() :
    ModularAdapter<CorpseElementsAdapter.BaseVH<CorpseElementsAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<CorpseElementsAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is CorpseElementHeaderVH.Item }) { CorpseElementHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is CorpseElementFileVH.Item }) { CorpseElementFileVH(it) })
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
}