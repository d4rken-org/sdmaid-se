package eu.darken.sdmse.main.ui.settings.general

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.R as CommonR

/** Picks which tools get their own scan + delete shortcut in the launcher's app-icon menu. */
@Composable
fun CleanShortcutsDialog(
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
    SdmConfirmDialog(
        title = stringResource(R.string.shortcuts_clean_title),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(android.R.string.ok),
            onClick = onDismiss,
        ),
    ) {
        Column {
            Text(
                text = stringResource(R.string.shortcuts_clean_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.shortcuts_clean_action_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.shortcuts_clean_launcher_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsDialogSwitchRow(
                label = stringResource(CommonR.string.corpsefinder_tool_name),
                checked = corpseFinderEnabled,
                onCheckedChange = onCorpseFinderChanged,
            )
            SettingsDialogSwitchRow(
                label = stringResource(CommonR.string.systemcleaner_tool_name),
                checked = systemCleanerEnabled,
                onCheckedChange = onSystemCleanerChanged,
            )
            SettingsDialogSwitchRow(
                label = stringResource(CommonR.string.appcleaner_tool_name),
                checked = appCleanerEnabled,
                onCheckedChange = onAppCleanerChanged,
            )
            SettingsDialogSwitchRow(
                label = stringResource(CommonR.string.deduplicator_tool_name),
                checked = deduplicatorEnabled,
                onCheckedChange = onDeduplicatorChanged,
            )
        }
    }
}

@Preview2
@Composable
private fun CleanShortcutsDialogPreview() {
    PreviewWrapper {
        CleanShortcutsDialog(
            corpseFinderEnabled = true,
            systemCleanerEnabled = false,
            appCleanerEnabled = true,
            deduplicatorEnabled = false,
            onCorpseFinderChanged = {},
            onSystemCleanerChanged = {},
            onAppCleanerChanged = {},
            onDeduplicatorChanged = {},
            onDismiss = {},
        )
    }
}
