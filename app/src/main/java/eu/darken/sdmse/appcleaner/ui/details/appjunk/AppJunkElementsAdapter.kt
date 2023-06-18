package eu.darken.sdmse.appcleaner.ui.details.appjunk

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementInaccessibleVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.common.lists.selection.SelectableItem
import javax.inject.Inject


class AppJunkElementsAdapter @Inject constructor() :
    ModularAdapter<AppJunkElementsAdapter.BaseVH<AppJunkElementsAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<AppJunkElementsAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is AppJunkElementHeaderVH.Item }) { AppJunkElementHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is AppJunkElementFileCategoryVH.Item }) {
            AppJunkElementFileCategoryVH(it)
        })
        addMod(TypedVHCreatorMod({ data[it] is AppJunkElementFileVH.Item }) { AppJunkElementFileVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is AppJunkElementInaccessibleVH.Item }) {
            AppJunkElementInaccessibleVH(
                it
            )
        })
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