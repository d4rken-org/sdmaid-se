package eu.darken.sdmse.setup.automation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessibilityNew
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import eu.darken.sdmse.common.R as CommonR

data class AutomationSetupCardItem(
    override val state: AutomationSetupModule.Result,
    val onGrantAction: () -> Unit,
    val onDismiss: () -> Unit,
    val onHelp: () -> Unit,
    val onRestrictionsHelp: () -> Unit,
    val onRestrictionsShow: () -> Unit,
) : SetupCardItem

@Composable
internal fun AutomationSetupCard(
    item: AutomationSetupCardItem,
    modifier: Modifier = Modifier,
) {
    val ui = item.state.toUiModel()
    SetupCardContainer(
        icon = Icons.TwoTone.AccessibilityNew,
        title = stringResource(R.string.setup_acs_card_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        Text(
            text = stringResource(R.string.setup_acs_card_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.setup_acs_card_body2),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        ui.enabledState?.let { chip ->
            StateChipRow(chip)
        }

        if (ui.showMiuiAutostartHint) {
            Text(
                text = stringResource(R.string.setup_acs_state_stopped_hint_miui),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )
        }

        if (ui.showAppOpsRestrictionHint) {
            AppOpsRestrictionBox(
                onHelp = item.onRestrictionsHelp,
                onShow = item.onRestrictionsShow,
            )
        }

        ui.runningState?.let { chip ->
            StateChipRow(chip)
        }

        if (ui.showRunningStateHint) {
            Text(
                text = stringResource(R.string.setup_acs_state_stopped_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )
        }

        if (ui.showAllowAction) {
            Button(
                onClick = item.onGrantAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(ui.allowActionText.textRes))
            }
        }

        if (ui.showShortcutHint) {
            Text(
                text = stringResource(R.string.setup_acs_allow_hint),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }

        if (ui.showDisallowAction) {
            FilledTonalButton(
                onClick = item.onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.setup_acs_consent_negative_action))
            }
        }
        if (ui.showDisallowHint) {
            Text(
                text = stringResource(R.string.setup_acs_disallow_hint),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun StateChipRow(chip: AutomationUiModel.StateChip) {
    val color = when (chip.tint) {
        AutomationUiModel.ChipTint.PRIMARY -> MaterialTheme.colorScheme.primary
        AutomationUiModel.ChipTint.SECONDARY -> MaterialTheme.colorScheme.secondary
        AutomationUiModel.ChipTint.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = chip.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(chip.textRes),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun AppOpsRestrictionBox(
    onHelp: () -> Unit,
    onShow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.setup_acs_appops_restriction_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = stringResource(R.string.setup_acs_appops_restriction_body),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Unspecified,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onHelp,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(CommonR.string.general_help_action))
            }
            Button(
                onClick = onShow,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(CommonR.string.general_view_action))
            }
        }
    }
}

@Preview2
@Composable
private fun AutomationSetupCardPreview() {
    PreviewWrapper {
        AutomationSetupCard(
            item = AutomationSetupCardItem(
                state = AutomationSetupModule.Result(
                    isNotRequired = false,
                    hasConsent = true,
                    canSelfEnable = false,
                    isServiceEnabled = true,
                    isServiceRunning = false,
                    isShortcutOrButtonEnabled = false,
                    needsXiaomiAutostart = false,
                    liftRestrictionsIntent = Intent(),
                    showAppOpsRestrictionHint = false,
                    settingsIntent = Intent(),
                ),
                onGrantAction = {},
                onDismiss = {},
                onHelp = {},
                onRestrictionsHelp = {},
                onRestrictionsShow = {},
            ),
        )
    }
}
