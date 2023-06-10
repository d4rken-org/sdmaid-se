package eu.darken.sdmse.appcontrol.ui.list

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.selection.SelectionTracker
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.appcontrol.core.AppInfo
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


class AppControlListAdapter @Inject constructor() :
    ModularAdapter<AppControlListAdapter.BaseVH<AppControlListAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<AppControlListAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    var tracker: SelectionTracker<Long>? = null

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is AppControlListRowVH.Item }) { AppControlListRowVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem, SelectableItem {
        val appInfo: AppInfo
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}