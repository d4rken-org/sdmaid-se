package eu.darken.sdmse.setup

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SetupModuleLoadingItemBinding


class SetupModuleLoadingCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<SetupModuleLoadingCardVH.Item, SetupModuleLoadingItemBinding>(
        R.layout.setup_module_loading_item,
        parent
    ) {


    override val viewBinding = lazy { SetupModuleLoadingItemBinding.bind(itemView) }

    override val onBindData: SetupModuleLoadingItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        icon.setImageResource(
            when (item.state.type) {
                SetupModule.Type.USAGE_STATS -> R.drawable.ic_chartbox_24
                SetupModule.Type.AUTOMATION -> R.drawable.ic_baseline_accessibility_new_24
                SetupModule.Type.SHIZUKU -> R.drawable.ic_shizuku_24
                SetupModule.Type.ROOT -> R.drawable.ic_root_24
                SetupModule.Type.NOTIFICATION -> R.drawable.ic_notification_24
                SetupModule.Type.SAF -> R.drawable.ic_saf
                SetupModule.Type.STORAGE -> R.drawable.ic_sd_storage
                SetupModule.Type.INVENTORY -> R.drawable.ic_apps
            }
        )
        title.setText(
            when (item.state.type) {
                SetupModule.Type.USAGE_STATS -> R.string.setup_usagestats_title
                SetupModule.Type.AUTOMATION -> R.string.setup_acs_card_title
                SetupModule.Type.SHIZUKU -> R.string.setup_shizuku_card_title
                SetupModule.Type.ROOT -> R.string.setup_root_card_title
                SetupModule.Type.NOTIFICATION -> R.string.setup_notification_title
                SetupModule.Type.SAF -> R.string.setup_saf_card_title
                SetupModule.Type.STORAGE -> R.string.setup_manage_storage_card_title
                SetupModule.Type.INVENTORY -> R.string.setup_inventory_card_title
            }
        )
        body.setText(eu.darken.sdmse.common.R.string.general_progress_loading)
    }

    data class Item(
        override val state: SetupModule.State.Loading,
    ) : SetupAdapter.Item
}