package eu.darken.sdmse.setup.automation

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessibilityNew
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import eu.darken.sdmse.setup.SetupLimitationBox
import eu.darken.sdmse.common.R as CommonR

data class AutomationSetupCardItem(
    override val state: AutomationSetupModule.Result,
    val onGrantAction: () -> Unit,
    val onDismiss: () -> Unit,
    val onHelp: () -> Unit,
    val onRestrictionsHelp: () -> Unit,
    val onRestrictionsShow: () -> Unit,
    val onAdvancedProtectionHelp: () -> Unit,
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

        if (ui.showAdvancedProtectionHint) {
            SetupLimitationBox(
                title = stringResource(R.string.setup_acs_advanced_protection_title),
                body = stringResource(R.string.setup_acs_advanced_protection_body),
            ) {
                Button(
                    onClick = item.onAdvancedProtectionHelp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(CommonR.string.general_help_action))
                }
            }
        }

        if (ui.showAppOpsRestrictionHint) {
            SetupLimitationBox(
                title = stringResource(R.string.setup_acs_appops_restriction_title),
                body = stringResource(R.string.setup_acs_appops_restriction_body),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = item.onRestrictionsHelp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(CommonR.string.general_help_action))
                    }
                    Button(
                        onClick = item.onRestrictionsShow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(CommonR.string.general_view_action))
                    }
                }
            }
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
                    showAdvancedProtectionHint = false,
                    isAdvancedProtectionBlocked = false,
                    settingsIntent = Intent(),
                ),
                onGrantAction = {},
                onDismiss = {},
                onHelp = {},
                onRestrictionsHelp = {},
                onRestrictionsShow = {},
                onAdvancedProtectionHelp = {},
            ),
        )
    }
}
