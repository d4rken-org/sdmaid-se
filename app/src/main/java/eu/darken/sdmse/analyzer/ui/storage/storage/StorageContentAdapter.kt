package eu.darken.sdmse.analyzer.ui.storage.storage

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.AppCategoryVH
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.MediaCategoryVH
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.SystemCategoryVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import javax.inject.Inject


class StorageContentAdapter @Inject constructor() :
    ModularAdapter<StorageContentAdapter.BaseVH<StorageContentAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<StorageContentAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is AppCategoryVH.Item }) { AppCategoryVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is MediaCategoryVH.Item }) { MediaCategoryVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SystemCategoryVH.Item }) { SystemCategoryVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}