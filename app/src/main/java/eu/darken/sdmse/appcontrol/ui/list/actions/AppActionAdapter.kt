package eu.darken.sdmse.appcontrol.ui.list.actions

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppStoreActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.SystemSettingsActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ToggleActionVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import javax.inject.Inject


class AppActionAdapter @Inject constructor() :
    ModularAdapter<AppActionAdapter.BaseVH<AppActionAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<AppActionAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is ToggleActionVH.Item }) { ToggleActionVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AppStoreActionVH.Item }) { AppStoreActionVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SystemSettingsActionVH.Item }) { SystemSettingsActionVH(it) })
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