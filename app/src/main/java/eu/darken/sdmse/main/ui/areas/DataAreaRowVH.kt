package eu.darken.sdmse.main.ui.areas

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.getShortLabel
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DataAreasListItemBinding


class DataAreaRowVH(parent: ViewGroup) :
    DataAreasAdapter.BaseVH<DataAreaRowVH.Item, DataAreasListItemBinding>(
        R.layout.data_areas_list_item,
        parent
    ) {

    override val viewBinding = lazy { DataAreasListItemBinding.bind(itemView) }

    override val onBindData: DataAreasListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val area = item.area
        icon.setImageResource(R.drawable.ic_sd_storage)
        primary.text = area.type.getShortLabel(context)
        secondary.text = area.path.userReadablePath.get(context)
    }

    data class Item(
        val area: DataArea,
    ) : DataAreasAdapter.Item {

        override val stableId: Long = area.path.hashCode().toLong()
    }

}