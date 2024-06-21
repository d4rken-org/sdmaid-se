package eu.darken.sdmse.setup.shizuku

import android.view.ViewGroup
import androidx.core.view.isGone
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
            when (item.state.useShizuku) {
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

        body.apply {
            text = getString(R.string.setup_shizuku_card_body)
            if (item.state.alsoHasRoot) {
                append("\n")
                append(getString(R.string.setup_shizuku_card_root_info))
            }
        }

        shizukuState.apply {
            isVisible = item.state.useShizuku == true && item.state.isInstalled
            text = getString(
                if (item.state.ourService) R.string.setup_shizuku_state_ready_label
                else R.string.setup_shizuku_state_waiting_label
            )
            setTextColor(
                if (item.state.ourService) context.getColorForAttr(android.R.attr.textColorSecondary)
                else context.getColorForAttr(android.R.attr.colorError)
            )
        }

        shizukuOpenAction.apply {
            setOnClickListener { item.onOpen() }
            isGone = !item.state.isInstalled || item.state.useShizuku != true || item.state.isComplete
        }

        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: ShizukuSetupModule.Result,
        val onToggleUseShizuku: (Boolean?) -> Unit,
        val onHelp: () -> Unit,
        val onOpen: () -> Unit,
    ) : SetupAdapter.Item
}