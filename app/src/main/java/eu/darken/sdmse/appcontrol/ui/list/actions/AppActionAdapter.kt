package eu.darken.sdmse.appcontrol.ui.list.actions

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ArchiveActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppStoreActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ExcludeActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ExportActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ForceStopActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.InfoSizeVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.InfoUsageVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.LaunchActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.SystemSettingsActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ToggleActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.UninstallActionVH
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
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is InfoSizeVH.Item }) { InfoSizeVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is InfoUsageVH.Item }) { InfoUsageVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is LaunchActionVH.Item }) { LaunchActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ForceStopActionVH.Item }) { ForceStopActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is UninstallActionVH.Item }) { UninstallActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ArchiveActionVH.Item }) { ArchiveActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ToggleActionVH.Item }) { ToggleActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is AppStoreActionVH.Item }) { AppStoreActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SystemSettingsActionVH.Item }) { SystemSettingsActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ExcludeActionVH.Item }) { ExcludeActionVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ExportActionVH.Item }) { ExportActionVH(it) })
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