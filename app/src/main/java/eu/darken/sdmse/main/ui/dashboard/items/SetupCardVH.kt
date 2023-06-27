package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardSetupItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.setup.SetupManager


class SetupCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<SetupCardVH.Item, DashboardSetupItemBinding>(R.layout.dashboard_setup_item, parent) {

    override val viewBinding = lazy { DashboardSetupItemBinding.bind(itemView) }

    override val onBindData: DashboardSetupItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        body.text = if (item.setupState.isHealerWorking) {
            getString(R.string.setup_incomplete_card_healing_in_progress_body)
        } else {
            getString(R.string.setup_incomplete_card_body)
        }

        dismissAction.apply {
            isInvisible = item.setupState.isHealerWorking
            setOnClickListener { item.onDismiss() }
        }

        setupProgress.isVisible = item.setupState.isHealerWorking
        continueSetupAction.apply {
            isInvisible = item.setupState.isHealerWorking
            setOnClickListener { item.onContinue() }
        }
    }

    data class Item(
        val setupState: SetupManager.State,
        val onDismiss: () -> Unit,
        val onContinue: () -> Unit
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}