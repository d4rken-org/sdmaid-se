package eu.darken.sdmse.setup.root

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SetupRootItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class RootSetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<RootSetupCardVH.Item, SetupRootItemBinding>(R.layout.setup_root_item, parent) {

    override val viewBinding = lazy { SetupRootItemBinding.bind(itemView) }

    override val onBindData: SetupRootItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        allowRootOptions.apply {
            setOnCheckedChangeListener(null)
            when (item.state.useRoot) {
                true -> check(R.id.allow_root_options_enable)
                false -> check(R.id.allow_root_options_disable)
                null -> check(-1)
            }
            setOnCheckedChangeListener { _, checkedId ->
                val selection = when (checkedId) {
                    R.id.allow_root_options_enable -> true
                    R.id.allow_root_options_disable -> false
                    else -> null
                }
                item.onToggleUseRoot(selection)
            }
        }
        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: RootSetupModule.State,
        val onToggleUseRoot: (Boolean?) -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}