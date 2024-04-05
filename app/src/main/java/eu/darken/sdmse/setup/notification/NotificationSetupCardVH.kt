package eu.darken.sdmse.setup.notification

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SetupUsagestatsItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class NotificationSetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<NotificationSetupCardVH.Item, SetupUsagestatsItemBinding>(
        R.layout.setup_notification_item,
        parent
    ) {

    override val viewBinding = lazy { SetupUsagestatsItemBinding.bind(itemView) }

    override val onBindData: SetupUsagestatsItemBinding.(
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
        override val state: NotificationSetupModule.Result,
        val onGrantAction: () -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item
}