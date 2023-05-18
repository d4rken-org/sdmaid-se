package eu.darken.sdmse.analyzer.ui.storage.app

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsAppCodeVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsExtraDataVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsHeaderVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsPrivateDataVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsPublicDataVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import javax.inject.Inject


class AppDetailsAdapter @Inject constructor() :
    ModularAdapter<AppDetailsAdapter.BaseVH<AppDetailsAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<AppDetailsAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is AppDetailsHeaderVH.Item }) { AppDetailsHeaderVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppDetailsAppCodeVH.Item }) { AppDetailsAppCodeVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppDetailsExtraDataVH.Item }) { AppDetailsExtraDataVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppDetailsPrivateDataVH.Item }) { AppDetailsPrivateDataVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppDetailsPublicDataVH.Item }) { AppDetailsPublicDataVH(it) })
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