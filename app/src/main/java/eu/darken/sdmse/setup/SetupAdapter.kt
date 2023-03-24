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
import eu.darken.sdmse.setup.accessibility.AccessibilitySetupCardVH
import eu.darken.sdmse.setup.notification.NotificationSetupCardVH
import eu.darken.sdmse.setup.root.RootSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.storage.StorageSetupCardVH
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardVH
import javax.inject.Inject


class SetupAdapter @Inject constructor() :
    ModularAdapter<SetupAdapter.BaseVH<SetupAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<SetupAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is StorageSetupCardVH.Item }) { StorageSetupCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is UsageStatsSetupCardVH.Item }) { UsageStatsSetupCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SAFSetupCardVH.Item }) { SAFSetupCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is AccessibilitySetupCardVH.Item }) { AccessibilitySetupCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is RootSetupCardVH.Item }) { RootSetupCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is NotificationSetupCardVH.Item }) { NotificationSetupCardVH(it) })
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