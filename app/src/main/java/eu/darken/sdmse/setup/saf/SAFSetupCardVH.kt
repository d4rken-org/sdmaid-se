package eu.darken.sdmse.setup.saf

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.databinding.SetupSafItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class SAFSetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<SAFSetupCardVH.Item, SetupSafItemBinding>(R.layout.setup_saf_item, parent) {

    private val pathAdapter = SAFCardPathAdapter()

    override val viewBinding = lazy {
        SetupSafItemBinding.bind(itemView).also {
            it.safItemList.setupDefaults(pathAdapter)
        }
    }

    override val onBindData: SetupSafItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        item.state.paths.map { pathAccess ->
            SAFCardPathAdapter.Item(
                pathAccess,
                onClicked = { item.onPathClicked(pathAccess) }
            )
        }.run { pathAdapter.update(this) }

        grantAction.apply {
            isGone = item.state.isComplete
            setOnClickListener {
                item.state.paths
                    .firstOrNull { !it.hasAccess }
                    ?.let { item.onPathClicked(it) }
            }
        }

        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: SAFSetupModule.State,
        val onPathClicked: (SAFSetupModule.State.PathAccess) -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}