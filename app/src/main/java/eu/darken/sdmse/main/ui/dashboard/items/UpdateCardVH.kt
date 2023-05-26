package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.ui.performClickWithRipple
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.databinding.DashboardUpdateItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class UpdateCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<UpdateCardVH.Item, DashboardUpdateItemBinding>(
        R.layout.dashboard_update_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardUpdateItemBinding.bind(itemView) }

    override val onBindData: DashboardUpdateItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        body.text = getString(
            R.string.updates_dashcard_body,
            "v${BuildConfigWrap.VERSION_NAME}",
            item.update.versionName,
        )

        viewAction.setOnClickListener { root.performClickWithRipple() }
        root.setOnClickListener { item.onViewUpdate() }
        dismissAction.setOnClickListener { item.onDismiss() }
        updateAction.setOnClickListener { item.onUpdate() }
    }

    data class Item(
        val update: UpdateChecker.Update,
        val onDismiss: () -> Unit,
        val onViewUpdate: () -> Unit,
        val onUpdate: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}