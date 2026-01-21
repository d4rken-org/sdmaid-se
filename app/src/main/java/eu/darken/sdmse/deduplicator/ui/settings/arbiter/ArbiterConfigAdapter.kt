package eu.darken.sdmse.deduplicator.ui.settings.arbiter

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
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject


class ArbiterConfigAdapter @Inject constructor() :
    ModularAdapter<ArbiterConfigAdapter.BaseVH<ArbiterConfigAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<ArbiterConfigAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is HeaderItem }) { ArbiterConfigHeaderVH(it) })
        addMod(TypedVHCreatorMod({ data[it] is CriteriumItem }) { ArbiterCriteriumRowVH(it) })
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

    data class CriteriumItem(
        val criterium: ArbiterCriterium,
        val position: Int,
        val onModeClicked: (CriteriumItem) -> Unit,
    ) : Item {
        override val stableId: Long = criterium::class.hashCode().toLong()
    }
}
