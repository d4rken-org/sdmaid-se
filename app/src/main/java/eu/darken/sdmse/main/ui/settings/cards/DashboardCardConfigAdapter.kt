package eu.darken.sdmse.main.ui.settings.cards

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.main.core.DashboardCardConfig
import javax.inject.Inject


class DashboardCardConfigAdapter @Inject constructor() :
    ModularAdapter<DashboardCardConfigAdapter.BaseVH<DashboardCardConfigAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DashboardCardConfigAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is HeaderItem }) { DashboardCardConfigHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is CardItem }) { DashboardCardConfigRowVH(it) })
    }

    private var dragStartListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    fun setOnDragStartListener(listener: (RecyclerView.ViewHolder) -> Unit) {
        dragStartListener = listener
    }

    fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        dragStartListener?.invoke(viewHolder)
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup,
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }

    data class HeaderItem(
        val id: String = "header",
    ) : Item {
        override val stableId: Long = id.hashCode().toLong()
    }

    class CardItem(
        val cardEntry: DashboardCardConfig.CardEntry,
        val position: Int,
        val onVisibilityToggle: (CardItem) -> Unit,
    ) : Item {
        override val stableId: Long = cardEntry.type.hashCode().toLong()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CardItem) return false
            return cardEntry == other.cardEntry
        }

        override fun hashCode(): Int = cardEntry.hashCode()
    }
}
