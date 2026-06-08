package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.compose.icons.Bug
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DebugTestResultSheet
import eu.darken.sdmse.main.ui.dashboard.cards.common.DebugTestSheet
import eu.darken.sdmse.main.ui.dashboard.cards.common.DebugToggleRow


data class DebugDashboardCardItem(
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
    val isLogPanelVisible: Boolean,
    val onToggleLogPanel: (Boolean) -> Unit,
    val onAcsDebug: () -> Unit,
    val acsTask: AutomationTask?,
    val onCheckUnknownFolders: () -> Unit,
    val isCheckingUnknownFolders: Boolean,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun DebugDashboardCard(item: DebugDashboardCardItem) {
    var activeSheet by remember { mutableStateOf<DebugTestSheet?>(null) }

    // Tests run async: pressing a button kicks off the work, the result flows in later with a fresh
    // testId. Open the sheet exactly once per completed run. Seeding the "last shown" id from the
    // current result means a result that already exists (recompose / config change) does NOT pop —
    // only a genuinely new testId does.
    var lastRootShown by rememberSaveable { mutableStateOf(item.rootTestResult?.testId) }
    val rootResult = item.rootTestResult
    LaunchedEffect(rootResult?.testId) {
        if (rootResult != null && rootResult.testId != lastRootShown) {
            lastRootShown = rootResult.testId
            activeSheet = DebugTestSheet.Root(rootResult)
        }
    }
    var lastShizukuShown by rememberSaveable { mutableStateOf(item.shizukuTestResult?.testId) }
    val shizukuResult = item.shizukuTestResult
    LaunchedEffect(shizukuResult?.testId) {
        if (shizukuResult != null && shizukuResult.testId != lastShizukuShown) {
            lastShizukuShown = shizukuResult.testId
            activeSheet = DebugTestSheet.Shizuku(shizukuResult)
        }
    }

    DashboardCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = SdmIcons.Bug,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.debug_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.debug_card_description),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.75f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Floating log panel toggle sits above dry-run for quick access.
        DebugToggleRow(
            text = "Floating log panel",
            description = "Draggable live log overlay",
            checked = item.isLogPanelVisible,
            onCheckedChange = item.onToggleLogPanel,
            highlight = item.isLogPanelVisible,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Runtime override kept up top: dry-run is a safety-relevant toggle.
        DebugToggleRow(
            text = stringResource(R.string.debug_card_dryrun_mode_title),
            description = stringResource(R.string.debug_card_dryrun_mode_desc),
            checked = item.isDryRunEnabled,
            onCheckedChange = item.onDryRunEnabled,
            highlight = item.isDryRunEnabled,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostics — results open in a bottom sheet once the async test completes.
        DebugSectionLabel(text = stringResource(R.string.debug_card_section_diagnostics))
        Spacer(modifier = Modifier.height(8.dp))
        DebugButtonRow {
            OutlinedButton(onClick = item.onTestRoot, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.debug_card_root_test_action))
            }
            OutlinedButton(onClick = item.onTestShizuku, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.debug_card_shizuku_test_action))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reload
        DebugSectionLabel(text = stringResource(R.string.debug_card_section_reload))
        Spacer(modifier = Modifier.height(8.dp))
        DebugButtonRow {
            OutlinedButton(onClick = item.onReloadAreas, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.debug_card_areas_reload_action))
            }
            OutlinedButton(onClick = item.onReloadPkgs, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.debug_card_pkgs_reload_action))
            }
        }

        // Deep-debug developer tools — labels intentionally hardcoded, debug-build only.
        if (BuildConfigWrap.DEBUG) {
            Spacer(modifier = Modifier.height(16.dp))
            DebugSectionLabel(text = "Developer")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = item.onCheckUnknownFolders,
                enabled = !item.isCheckingUnknownFolders,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (item.isCheckingUnknownFolders) "Checking…" else "Check unknown folders")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = item.onRunTest, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Run tests")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = item.onAcsDebug, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (item.acsTask != null) "Stop ACS debug" else "Start ACS debug")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.15f))
        Spacer(modifier = Modifier.height(16.dp))

        // Trace logging lives at the bottom — it's the noisiest, least-frequently-flipped toggle.
        DebugToggleRow(
            text = stringResource(R.string.debug_card_trace_mode_title),
            description = stringResource(R.string.debug_card_trace_mode_desc),
            checked = item.isTraceEnabled,
            onCheckedChange = item.onTraceEnabled,
        )
    }

    activeSheet?.let { sheet ->
        DebugTestResultSheet(
            sheet = sheet,
            onDismiss = { activeSheet = null },
        )
    }
}

@Composable
private fun DebugSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DebugButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Preview2
@Composable
private fun DebugDashboardCardPreview() {
    PreviewWrapper {
        DebugDashboardCard(
            item = DebugDashboardCardItem(
                isDryRunEnabled = true,
                onDryRunEnabled = {},
                isTraceEnabled = false,
                onTraceEnabled = {},
                onReloadAreas = {},
                onReloadPkgs = {},
                onRunTest = {},
                rootTestResult = null,
                onTestRoot = {},
                shizukuTestResult = null,
                onTestShizuku = {},
                isLogPanelVisible = false,
                onToggleLogPanel = {},
                onAcsDebug = {},
                acsTask = null,
                onCheckUnknownFolders = {},
                isCheckingUnknownFolders = false,
            ),
        )
    }
}
