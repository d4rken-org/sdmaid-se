package eu.darken.sdmse.setup.inventory

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.ui.setLeftIcon
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
        val state = item.state

        grantState.apply {
            setTextColor(
                getColorForAttr(
                    when {
                        state.isAccessFaked -> com.google.android.material.R.attr.colorError
                        else -> com.google.android.material.R.attr.colorPrimary
                    }
                )
            )
            text = when {
                state.isAccessFaked -> getString(R.string.setup_permission_error_label)
                else -> getString(R.string.setup_permission_granted_label)
            }

            when {
                state.isAccessFaked -> setLeftIcon(
                    R.drawable.ic_error_onsurface,
                    com.google.android.material.R.attr.colorError
                )

                else -> setLeftIcon(
                    R.drawable.ic_check_circle,
                    com.google.android.material.R.attr.colorPrimary
                )
            }

            isGone = item.state.missingPermission.isNotEmpty()
        }

        grantHint.apply {
            text = getString(R.string.setup_inventory_invalid_message)
            isGone = !state.isAccessFaked
        }

        grantAction.apply {
            text = when {
                state.isAccessFaked -> getString(eu.darken.sdmse.common.R.string.general_open_system_settings_action)
                else -> getString(eu.darken.sdmse.common.R.string.general_grant_access_action)
            }
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