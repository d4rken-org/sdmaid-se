package eu.darken.sdmse.setup.inventory

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SetupInventoryItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class InventorySetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<InventorySetupCardVH.Item, SetupInventoryItemBinding>(
        R.layout.setup_inventory_item,
        parent
    ) {

    override val viewBinding = lazy { SetupInventoryItemBinding.bind(itemView) }

    override val onBindData: SetupInventoryItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        grantState.isGone = item.state.missingPermission.isNotEmpty()

        grantAction.apply {
            isGone = item.state.isComplete
            setOnClickListener { item.onGrantAction() }
        }
        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: InventorySetupModule.Result,
        val onGrantAction: () -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item
}