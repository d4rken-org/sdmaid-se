package eu.darken.sdmse.setup.storage

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.databinding.SetupStorageItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class StorageSetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<StorageSetupCardVH.Item, SetupStorageItemBinding>(R.layout.setup_storage_item, parent) {

    private val pathAdapter = LocalPathCardAdapter()

    override val viewBinding = lazy {
        SetupStorageItemBinding.bind(itemView).also {
            it.pathItemList.setupDefaults(pathAdapter)
        }
    }

    override val onBindData: SetupStorageItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        item.state.paths.map { pathAccess ->
            LocalPathCardAdapter.Item(
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
        override val state: StorageSetupModule.State,
        val onPathClicked: (StorageSetupModule.State.PathAccess) -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}