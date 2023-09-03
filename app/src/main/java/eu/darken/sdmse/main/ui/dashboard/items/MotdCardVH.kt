package eu.darken.sdmse.main.ui.dashboard.items

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardMotdItemBinding
import eu.darken.sdmse.main.core.motd.Motd
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class MotdCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<MotdCardVH.Item, DashboardMotdItemBinding>(
        R.layout.dashboard_motd_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardMotdItemBinding.bind(itemView) }

    override val onBindData: DashboardMotdItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        body.apply {
            text = item.motd.message
            autoLinkMask = Linkify.ALL
            movementMethod = LinkMovementMethod.getInstance()
        }

        primaryAction.apply {
            setOnClickListener { item.onPrimary() }
            isGone = item.motd.primaryLink == null
        }
        dismissAction.setOnClickListener { item.onDismiss() }
    }

    data class Item(
        val motd: Motd,
        val onPrimary: () -> Unit,
        val onDismiss: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}