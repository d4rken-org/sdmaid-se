package eu.darken.sdmse.main.ui.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR

@Composable
fun OneClickOptionsDialog(
    corpseFinderEnabled: Boolean,
    systemCleanerEnabled: Boolean,
    appCleanerEnabled: Boolean,
    deduplicatorEnabled: Boolean,
    onCorpseFinderChanged: (Boolean) -> Unit,
    onSystemCleanerChanged: (Boolean) -> Unit,
    onAppCleanerChanged: (Boolean) -> Unit,
    onDeduplicatorChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dashboard_settings_oneclick_tools_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_settings_oneclick_tools_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OneClickSwitchRow(
                    label = stringResource(CommonR.string.corpsefinder_tool_name),
                    checked = corpseFinderEnabled,
                    onCheckedChange = onCorpseFinderChanged,
                )
                OneClickSwitchRow(
                    label = stringResource(CommonR.string.systemcleaner_tool_name),
                    checked = systemCleanerEnabled,
                    onCheckedChange = onSystemCleanerChanged,
                )
                OneClickSwitchRow(
                    label = stringResource(CommonR.string.appcleaner_tool_name),
                    checked = appCleanerEnabled,
                    onCheckedChange = onAppCleanerChanged,
                )
                OneClickSwitchRow(
                    label = stringResource(CommonR.string.deduplicator_tool_name),
                    checked = deduplicatorEnabled,
                    onCheckedChange = onDeduplicatorChanged,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun OneClickSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
