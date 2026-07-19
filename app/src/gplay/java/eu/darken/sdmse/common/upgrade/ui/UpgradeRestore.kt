package eu.darken.sdmse.common.upgrade.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

// Described restore section, shared by all restore audiences (copy and emphasis differ, wiring
// doesn't). Deliberately NO contact-support action here: escalation is offered only after a
// restore came up empty (the failed-restore dialog), so self-service gets its chance first.
@Composable
internal fun UpgradeRestoreSection(
    title: String,
    body: String,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    restoreInProgress: Boolean = false,
    emphasized: Boolean = false,
    restoreTag: String = UpgradeScreenTags.GPLAY_RESTORE,
) {
    UpgradeSectionCard(
        title = title,
        icon = Icons.TwoTone.Restore,
        modifier = modifier,
        colors = if (emphasized) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            null
        },
    ) {
        if (emphasized) {
            // Plain Text: the tinted container brings its own content color, the muted
            // UpgradeSectionBody tone is for neutral surface cards only.
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            UpgradeSectionBody(text = body)
        }
        if (emphasized) {
            Button(
                onClick = onRestore,
                enabled = !restoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(restoreTag),
            ) {
                RestoreButtonLabel(restoreInProgress = restoreInProgress)
            }
        } else {
            OutlinedButton(
                onClick = onRestore,
                enabled = !restoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(restoreTag),
            ) {
                RestoreButtonLabel(restoreInProgress = restoreInProgress)
            }
        }
    }
}

@Composable
private fun RestoreButtonLabel(restoreInProgress: Boolean) {
    if (restoreInProgress) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(stringResource(R.string.upgrade_screen_restore_purchase_action))
}

@Preview2
@Composable
private fun UpgradeRestoreSectionPreview() {
    PreviewWrapper {
        UpgradeRestoreSection(
            title = "Already bought Pro?",
            body = "Restoring asks Google Play to re-check this app's purchases for the current account.",
            onRestore = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeRestoreSectionEmphasizedPreview() {
    PreviewWrapper {
        UpgradeRestoreSection(
            title = "Already bought Pro?",
            body = "It looks like you upgraded to Pro on this device before.",
            onRestore = {},
            emphasized = true,
            restoreInProgress = true,
        )
    }
}
