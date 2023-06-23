package eu.darken.sdmse.main.ui.dashboard.items

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.databinding.DashboardDebugItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class DebugCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DebugCardVH.Item, DashboardDebugItemBinding>(R.layout.dashboard_debug_item, parent) {

    override val viewBinding = lazy { DashboardDebugItemBinding.bind(itemView) }

    @SuppressLint("SetTextI18n")
    override val onBindData: DashboardDebugItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        traceEnabled.apply {
            setChecked2(item.isTraceEnabled)
            setOnCheckedChangeListener { _, isChecked -> item.onTraceEnabled(isChecked) }
        }
        dryrunEnabled.apply {
            setChecked2(item.isDryRunEnabled)
            setOnCheckedChangeListener { _, isChecked -> item.onDryRunEnabled(isChecked) }
        }
        pkgsReloadAction.setOnClickListener { item.onReloadPkgs() }
        areasReloadAction.setOnClickListener { item.onReloadAreas() }

        rootTestState.apply {
            isVisible = item.rootTestResult != null
            val result = item.rootTestResult
            val sb = StringBuilder()
            sb.append("Consent=${result?.hasUserConsent}\n")
            sb.append("MagiskGrant=${result?.magiskGranted}\n")
            sb.append("${result?.serviceLaunched}")
            text = sb.toString()
        }
        rootTestAction.setOnClickListener { item.onTestRoot() }

        shizukuTestState.apply {
            isVisible = item.shizukuTestResult != null
            val result = item.shizukuTestResult
            val sb = StringBuilder()
            sb.append("Installed=${result?.isInstalled}\n")
            sb.append("Consent=${result?.hasUserConsent}\n")
            sb.append("ShizukuGrant=${result?.isGranted}\n")
            sb.append("${result?.serviceLaunched}")
            text = sb.toString()
        }
        shizukuTestAction.setOnClickListener { item.onTestShizuku() }

        testAction.setOnClickListener { item.onRunTest() }
        testAction.isVisible = BuildConfigWrap.DEBUG
        logviewAction.isVisible = BuildConfigWrap.DEBUG
        logviewAction.setOnClickListener { item.onViewLog() }
    }

    data class Item(
        val isDryRunEnabled: Boolean,
        val onDryRunEnabled: (Boolean) -> Unit,
        val isTraceEnabled: Boolean,
        val onTraceEnabled: (Boolean) -> Unit,
        val onReloadAreas: () -> Unit,
        val onReloadPkgs: () -> Unit,
        val onRunTest: () -> Unit,
        val rootTestResult: DebugCardProvider.RootTestResult?,
        val onTestRoot: () -> Unit,
        val shizukuTestResult: DebugCardProvider.ShizukuTestResult?,
        val onTestShizuku: () -> Unit,
        val onViewLog: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}