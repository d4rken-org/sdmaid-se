package eu.darken.sdmse.setup.shizuku

import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SetupShizukuItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class ShizukuSetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<ShizukuSetupCardVH.Item, SetupShizukuItemBinding>(R.layout.setup_shizuku_item, parent) {

    override val viewBinding = lazy { SetupShizukuItemBinding.bind(itemView) }

    override val onBindData: SetupShizukuItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        shizukuState.apply {
            text = getString(
                if (item.state.binderAvailable) R.string.setup_shizuku_state_ready_label
                else R.string.setup_shizuku_state_waiting_label
            )
            setTextColor(
                if (item.state.binderAvailable) context.getColorForAttr(android.R.attr.textColorSecondary)
                else context.getColorForAttr(android.R.attr.colorError)
            )
        }

        grantState.isInvisible = !item.state.isGranted
        grantAction.apply {
            isVisible = !item.state.isGranted
            isEnabled = item.state.binderAvailable
            setOnClickListener { item.onGrant() }
        }

        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: ShizukuSetupModule.State,
        val onGrant: () -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}