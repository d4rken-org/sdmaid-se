package eu.darken.sdmse.main.ui.dashboard.items

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardMotdItemBinding
import eu.darken.sdmse.main.core.motd.MotdState
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
            text = item.state.motd.message
            autoLinkMask = Linkify.ALL
            movementMethod = LinkMovementMethod.getInstance()
        }

        primaryAction.apply {
            setOnClickListener { item.onPrimary() }
            isGone = item.state.motd.primaryLink == null
        }
        dismissAction.setOnClickListener { item.onDismiss() }
        translateAction.apply {
            setOnClickListener { item.onTranslate() }
            isVisible = item.state.allowTranslation
        }
    }

    data class Item(
        val state: MotdState,
        val onPrimary: () -> Unit,
        val onTranslate: () -> Unit,
        val onDismiss: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}