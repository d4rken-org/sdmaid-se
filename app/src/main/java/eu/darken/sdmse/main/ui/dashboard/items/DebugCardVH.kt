package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardDebugItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class DebugCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DebugCardVH.Item, DashboardDebugItemBinding>(R.layout.dashboard_debug_item, parent) {

    override val viewBinding = lazy { DashboardDebugItemBinding.bind(itemView) }

    override val onBindData: DashboardDebugItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        traceEnabled.apply {
            isChecked = item.isTraceEnabled
            setOnCheckedChangeListener { _, isChecked -> item.onTraceEnabled(isChecked) }
        }
        pkgsReloadAction.setOnClickListener { item.onReloadPkgs() }
        areasReloadAction.setOnClickListener { item.onReloadAreas() }
        testAction.setOnClickListener { item.onRunTest() }
        testAction.isVisible = BuildConfigWrap.DEBUG
    }

    data class Item(
        val isTraceEnabled: Boolean,
        val onTraceEnabled: (Boolean) -> Unit,
        val onReloadAreas: () -> Unit,
        val onReloadPkgs: () -> Unit,
        val onRunTest: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}