package eu.darken.sdmse.main.ui.dashboard

import android.app.Activity
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import dagger.hilt.android.scopes.ActivityScoped
import eu.darken.sdmse.analyzer.ui.AnalyzerDashCardVH
import eu.darken.sdmse.appcontrol.ui.AppControlDashCardVH
import eu.darken.sdmse.common.debug.recorder.ui.DebugRecorderCardVH
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.main.ui.dashboard.items.DataAreaCardVH
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import eu.darken.sdmse.main.ui.dashboard.items.MotdCardVH
import eu.darken.sdmse.main.ui.dashboard.items.ReviewCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SetupCardVH
import eu.darken.sdmse.main.ui.dashboard.items.TitleCardVH
import eu.darken.sdmse.main.ui.dashboard.items.UpdateCardVH
import eu.darken.sdmse.main.ui.dashboard.items.UpgradeCardVH
import eu.darken.sdmse.scheduler.ui.SchedulerDashCardVH
import eu.darken.sdmse.stats.ui.StatsDashCardVH
import javax.inject.Inject

@ActivityScoped
class DashboardAdapter @Inject constructor(
    private val activity: Activity,
) :
    ModularAdapter<DashboardAdapter.BaseVH<DashboardAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DashboardAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is TitleCardVH.Item }) { TitleCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is DebugCardVH.Item }) { DebugCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SetupCardVH.Item }) { SetupCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is UpgradeCardVH.Item }) { UpgradeCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is UpdateCardVH.Item }) { UpdateCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is DataAreaCardVH.Item }) { DataAreaCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is DashboardToolCard.Item }) { DashboardToolCard(it) })
        addMod(TypedVHCreatorMod({ data[it] is AppControlDashCardVH.Item }) { AppControlDashCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is AnalyzerDashCardVH.Item }) { AnalyzerDashCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SchedulerDashCardVH.Item }) { SchedulerDashCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is DebugRecorderCardVH.Item }) { DebugRecorderCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is MotdCardVH.Item }) { MotdCardVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is ReviewCardVH.Item }) { ReviewCardVH(activity, it) })
        addMod(TypedVHCreatorMod({ data[it] is StatsDashCardVH.Item }) { StatsDashCardVH(it) })
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