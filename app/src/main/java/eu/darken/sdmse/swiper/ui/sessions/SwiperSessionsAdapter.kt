package eu.darken.sdmse.swiper.ui.sessions

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
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsHeaderVH
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsSessionVH
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsUpgradeVH

class SwiperSessionsAdapter :
    ModularAdapter<SwiperSessionsAdapter.BaseVH<SwiperSessionsAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<SwiperSessionsAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is SwiperSessionsHeaderVH.Item }) { SwiperSessionsHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SwiperSessionsUpgradeVH.Item }) { SwiperSessionsUpgradeVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is SwiperSessionsSessionVH.Item }) { SwiperSessionsSessionVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup,
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }
}
