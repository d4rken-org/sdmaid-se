package eu.darken.sdmse.systemcleaner.ui.customfilter.list.types

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.SystemcleanerCustomfilterListItemBinding
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.ui.customfilter.list.CustomFilterListAdapter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class CustomFilterDefaultVH(parent: ViewGroup) :
    CustomFilterListAdapter.BaseVH<CustomFilterDefaultVH.Item, SystemcleanerCustomfilterListItemBinding>(
        R.layout.systemcleaner_customfilter_list_item,
        parent
    ) {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { SystemcleanerCustomfilterListItemBinding.bind(itemView) }

    override val onBindData: SystemcleanerCustomfilterListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item

        icon.setOnClickListener { item.onEditClick(item) }

        primary.text = item.config.label

        secondary.text = getString(
            R.string.systemcleaner_customfilter_last_edit,
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(item.config.modifiedAt.toSystemTimezone()),
        )

        toggleAction.isChecked = item.isEnabled

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        override val config: CustomFilterConfig,
        val isEnabled: Boolean,
        val onItemClick: (Item) -> Unit,
        val onEditClick: (Item) -> Unit,
    ) : CustomFilterListAdapter.Item {
        override val stableId: Long = config.identifier.hashCode().toLong()
        override val itemSelectionKey: String = config.identifier
    }

}