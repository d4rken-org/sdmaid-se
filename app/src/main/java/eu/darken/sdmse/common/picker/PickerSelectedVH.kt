package eu.darken.sdmse.common.picker

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.CommonPickerSelectedItemBinding


class PickerSelectedVH(parent: ViewGroup) :
    PickerSelectedAdapter.BaseVH<PickerSelectedVH.Item, CommonPickerSelectedItemBinding>(
        R.layout.common_picker_selected_item,
        parent
    ) {

    override val viewBinding = lazy { CommonPickerSelectedItemBinding.bind(itemView) }

    override val onBindData: CommonPickerSelectedItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        icon.setImageResource(item.lookup.fileType.iconRes)
        primary.text = item.lookup.userReadablePath.get(context)
        removeAction.setOnClickListener { item.onRemove() }
    }

    data class Item(
        val lookup: APathLookup<*>,
        val onRemove: () -> Unit,
    ) : PickerSelectedAdapter.Item {
        override val stableId: Long = lookup.path.hashCode().toLong()
    }

}