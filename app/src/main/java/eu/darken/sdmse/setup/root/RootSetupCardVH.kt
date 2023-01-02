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

        allowRootToggle.apply {
            isChecked = item.setupState.useRoot ?: false
            setOnCheckedChangeListener { _, isChecked -> item.onToggleUseRoot(isChecked) }
        }
        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        val setupState: RootSetupModule.State,
        val onToggleUseRoot: (Boolean) -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}