package eu.darken.sdmse.setup

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
import eu.darken.sdmse.setup.automation.AutomationSetupCardVH
import eu.darken.sdmse.setup.notification.NotificationSetupCardVH
import eu.darken.sdmse.setup.root.RootSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardVH
import eu.darken.sdmse.setup.storage.StorageSetupCardVH
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardVH
import javax.inject.Inject


class SetupAdapter @Inject constructor() :
    ModularAdapter<SetupAdapter.BaseVH<SetupAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<SetupAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is StorageSetupCardVH.Item }) { StorageSetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is UsageStatsSetupCardVH.Item }) { UsageStatsSetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SAFSetupCardVH.Item }) { SAFSetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is AutomationSetupCardVH.Item }) { AutomationSetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is RootSetupCardVH.Item }) { RootSetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is NotificationSetupCardVH.Item }) { NotificationSetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ShizukuSetupCardVH.Item }) { ShizukuSetupCardVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {

        val state: SetupModule.State

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}