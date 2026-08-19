package eu.darken.sdmse.main.ui.settings.general

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.labelRes

/**
 * Picks which tools get a launcher shortcut. [tools] arrives in publish order (the user's dashboard
 * card order), so the rows show the order the launcher menu will use.
 */
@Composable
fun ShortcutToolsDialog(
    tools: List<DashboardCardType>,
    enabledTools: Set<DashboardCardType>,
    onToolChanged: (DashboardCardType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SdmConfirmDialog(
        title = stringResource(R.string.shortcuts_tools_title),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(android.R.string.ok),
            onClick = onDismiss,
        ),
    ) {
        // All ten tools don't fit the dialog's max height on smaller screens.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.shortcuts_tools_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.shortcuts_tools_launcher_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tools.forEach { type ->
                SettingsDialogSwitchRow(
                    label = stringResource(type.labelRes),
                    checked = enabledTools.contains(type),
                    onCheckedChange = { onToolChanged(type, it) },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun ShortcutToolsDialogPreview() {
    PreviewWrapper {
        ShortcutToolsDialog(
            tools = DashboardCardType.entries,
            enabledTools = setOf(DashboardCardType.APPCONTROL, DashboardCardType.APPCLEANER),
            onToolChanged = { _, _ -> },
            onDismiss = {},
        )
    }
}
