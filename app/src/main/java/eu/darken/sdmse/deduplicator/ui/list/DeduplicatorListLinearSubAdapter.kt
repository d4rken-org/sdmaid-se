package eu.darken.sdmse.deduplicator.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.DeduplicatorListLinearSubItemBinding
import eu.darken.sdmse.deduplicator.core.Duplicate
import javax.inject.Inject

class DeduplicatorListLinearSubAdapter @Inject constructor() :
    ModularAdapter<DeduplicatorListLinearSubAdapter.BaseVH<DeduplicatorListLinearSubAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DeduplicatorListLinearSubAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is DuplicateItemVH.Item }) { DuplicateItemVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem, SelectableItem {
        val cluster: Duplicate.Cluster
        val dupe: Duplicate

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }

    class DuplicateItemVH(parent: ViewGroup) : BaseVH<DuplicateItemVH.Item, DeduplicatorListLinearSubItemBinding>(
        R.layout.deduplicator_list_linear_sub_item,
        parent
    ), SelectableVH {

        private var lastItem: Item? = null
        override val itemSelectionKey: String?
            get() = lastItem?.itemSelectionKey

        override fun updatedSelectionState(selected: Boolean) {
            itemView.isSelected = selected
        }

        override val viewBinding = lazy { DeduplicatorListLinearSubItemBinding.bind(itemView) }

        override val onBindData: DeduplicatorListLinearSubItemBinding.(
            item: Item,
            payloads: List<Any>
        ) -> Unit = binding { item ->
            lastItem = item
            val dupe = item.dupe

            val fileName = dupe.path.userReadableName.get(context)
            name.text = fileName
            path.text = dupe.path.userReadablePath.get(context).replace(fileName, "")

            secondary.text = Formatter.formatShortFileSize(context, dupe.size)

            root.setOnClickListener { item.onItemClicked(item) }
        }

        data class Item(
            override val cluster: Duplicate.Cluster,
            override val dupe: Duplicate,
            val onItemClicked: (Item) -> Unit,
        ) : DeduplicatorListLinearSubAdapter.Item {

            override val itemSelectionKey: String
                get() = dupe.identifier.toString()

            override val stableId: Long = dupe.identifier.hashCode().toLong()
        }
    }
}