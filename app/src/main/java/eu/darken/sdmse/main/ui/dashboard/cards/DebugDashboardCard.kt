package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH

@Composable
internal fun DebugDashboardCard(item: DebugCardVH.Item) {
    DashboardCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = stringResource(R.string.debug_card_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        DebugToggleRow(
            text = stringResource(R.string.debug_card_trace_mode_label),
            checked = item.isTraceEnabled,
            onCheckedChange = item.onTraceEnabled,
        )
        DebugToggleRow(
            text = stringResource(R.string.debug_card_dryrun_mode_label),
            checked = item.isDryRunEnabled,
            onCheckedChange = item.onDryRunEnabled,
            highlight = item.isDryRunEnabled,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = item.onReloadAreas,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.debug_card_areas_reload_action))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = item.onReloadPkgs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.debug_card_pkgs_reload_action))
        }

        item.rootTestResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Consent=${it.hasUserConsent}\n")
                    append("MagiskGrant=${it.magiskGranted}\n")
                    append("${it.serviceLaunched}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = item.onTestRoot,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.debug_card_root_test_action))
        }

        item.shizukuTestResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Installed=${it.isInstalled}\n")
                    append("Consent=${it.hasUserConsent}\n")
                    append("ShizukuGrant=${it.isGranted}\n")
                    append("${it.serviceLaunched}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = item.onTestShizuku,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.debug_card_shizuku_test_action))
        }

        if (BuildConfigWrap.DEBUG) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = item.onCheckUnknownFolders,
                enabled = !item.isCheckingUnknownFolders,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (item.isCheckingUnknownFolders) "Checking..." else "Check unknown folders")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = item.onRunTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Run tests")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = item.onViewLog,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "View log")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = item.onAcsDebug,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (item.acsTask != null) "Stop ACS debug" else "Start ACS debug")
            }
        }
    }
}

@Preview2
@Composable
private fun DebugDashboardCardPreview() {
    PreviewWrapper {
        DebugDashboardCard(
            item = DebugCardVH.Item(
                isDryRunEnabled = true,
                onDryRunEnabled = {},
                isTraceEnabled = false,
                onTraceEnabled = {},
                onReloadAreas = {},
                onReloadPkgs = {},
                onRunTest = {},
                rootTestResult = DebugCardProvider.RootTestResult(
                    testId = "root",
                    hasUserConsent = true,
                    magiskGranted = true,
                    serviceLaunched = "ok",
                ),
                onTestRoot = {},
                shizukuTestResult = DebugCardProvider.ShizukuTestResult(
                    testId = "shizuku",
                    hasUserConsent = true,
                    isInstalled = true,
                    isGranted = true,
                    serviceLaunched = "ok",
                ),
                onTestShizuku = {},
                onViewLog = {},
                onAcsDebug = {},
                acsTask = null,
                onCheckUnknownFolders = {},
                isCheckingUnknownFolders = false,
            ),
        )
    }
}
