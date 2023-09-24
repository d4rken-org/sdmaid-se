package eu.darken.sdmse.setup.shizuku

import android.view.ViewGroup
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
        allowShizukuOptions.apply {
            setOnCheckedChangeListener(null)
            when (item.state.isEnabled) {
                true -> check(R.id.allow_shizuku_options_enable)
                false -> check(R.id.allow_shizuku_options_disable)
                null -> check(-1)
            }
            setOnCheckedChangeListener { _, checkedId ->
                val selection = when (checkedId) {
                    R.id.allow_shizuku_options_enable -> true
                    R.id.allow_shizuku_options_disable -> false
                    else -> null
                }
                item.onToggleUseShizuku(selection)
            }
        }

        shizukuState.apply {
            isVisible = item.state.isEnabled == true
            text = getString(
                if (item.state.ourService) R.string.setup_shizuku_state_ready_label
                else R.string.setup_shizuku_state_waiting_label
            )
            setTextColor(
                if (item.state.ourService) context.getColorForAttr(android.R.attr.textColorSecondary)
                else context.getColorForAttr(android.R.attr.colorError)
            )
        }

        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: ShizukuSetupModule.State,
        val onToggleUseShizuku: (Boolean?) -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}