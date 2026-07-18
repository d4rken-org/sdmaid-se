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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

// Restore is account reconciliation, not an offer: it lives in its own described section instead
// of dangling under the purchase options. All variants share the same restore wiring (the
// single-flight restorePurchase() with its pause semantics) and differ only in copy and emphasis;
// each ends in a support escape hatch for the cases self-service can't fix (refunds gone wrong,
// account mix-ups Play won't resolve).
@Composable
internal fun UpgradeRestoreSection(
    title: String,
    body: String,
    onRestore: () -> Unit,
    onContactSupport: () -> Unit,
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
        TextButton(
            onClick = onContactSupport,
            modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_CONTACT_SUPPORT),
        ) {
            Text(stringResource(R.string.upgrade_screen_contact_support_action))
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
            onContactSupport = {},
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
            onContactSupport = {},
            emphasized = true,
            restoreInProgress = true,
        )
    }
}
