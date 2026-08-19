package eu.darken.sdmse.setup.inventory

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material3.Button
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
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import eu.darken.sdmse.setup.SetupLimitationBox
import eu.darken.sdmse.setup.inventory.InventorySetupModule.InventoryAccess
import eu.darken.sdmse.common.R as CommonR

data class InventorySetupCardItem(
    override val state: InventorySetupModule.Result,
    val onGrantAction: () -> Unit,
    val onHelp: () -> Unit,
    val onRetry: () -> Unit,
) : SetupCardItem

@Composable
internal fun InventorySetupCard(
    item: InventorySetupCardItem,
    modifier: Modifier = Modifier,
) {
    SetupCardContainer(
        icon = Icons.TwoTone.Apps,
        title = stringResource(R.string.setup_inventory_card_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        Text(
            text = stringResource(R.string.setup_inventory_card_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        // An incomplete list or a failed probe is left to the limitation boxes below: they already carry
        // a warning icon, their own title and a body that explains the state, so this row was a second
        // error header with strictly less information.
        if (item.state.missingPermission.isEmpty() && item.state.access is InventoryAccess.Valid) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.setup_permission_granted_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (item.state.access is InventoryAccess.Incomplete) {
            SetupLimitationBox(
                title = stringResource(R.string.setup_inventory_limitation_title),
                body = stringResource(R.string.setup_inventory_limitation_body),
                body2 = stringResource(R.string.setup_inventory_limitation_body2),
            ) {
                Button(
                    onClick = item.onGrantAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(CommonR.string.general_open_system_settings_action))
                }
                OutlinedButton(
                    onClick = item.onHelp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(CommonR.string.general_help_action))
                }
            }
        }

        if (item.state.access is InventoryAccess.ProbeFailed) {
            // No "Open system settings" here: the permissions are granted, the request itself failed,
            // so there is nothing for the user to change in the settings page.
            SetupLimitationBox(
                title = stringResource(R.string.setup_inventory_probe_failed_title),
                body = stringResource(R.string.setup_inventory_probe_failed_body),
            ) {
                Button(
                    onClick = item.onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(CommonR.string.general_retry_action))
                }
                OutlinedButton(
                    onClick = item.onHelp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(CommonR.string.general_help_action))
                }
            }
        }

        if (item.state.missingPermission.isNotEmpty()) {
            Button(
                onClick = item.onGrantAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(CommonR.string.general_grant_access_action))
            }
        }

        Text(
            text = stringResource(R.string.setup_inventory_card_extra),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Preview2
@Composable
private fun InventorySetupCardInvalidPreview() {
    PreviewWrapper {
        InventorySetupCard(
            item = InventorySetupCardItem(
                state = InventorySetupModule.Result(
                    missingPermission = emptySet(),
                    access = InventorySetupModule.InventoryAccess.Incomplete,
                    settingsIntent = Intent(),
                ),
                onGrantAction = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun InventorySetupCardProbeFailedPreview() {
    PreviewWrapper {
        InventorySetupCard(
            item = InventorySetupCardItem(
                state = InventorySetupModule.Result(
                    missingPermission = emptySet(),
                    access = InventorySetupModule.InventoryAccess.ProbeFailed,
                    settingsIntent = Intent(),
                ),
                onGrantAction = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun InventorySetupCardMissingPermissionPreview() {
    PreviewWrapper {
        InventorySetupCard(
            item = InventorySetupCardItem(
                state = InventorySetupModule.Result(
                    missingPermission = setOf(Permission.GET_INSTALLED_APPS),
                    access = InventorySetupModule.InventoryAccess.NotChecked,
                    settingsIntent = Intent(),
                ),
                onGrantAction = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}
