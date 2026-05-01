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
import androidx.compose.material.icons.twotone.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import eu.darken.sdmse.common.R as CommonR

data class InventorySetupCardItem(
    override val state: InventorySetupModule.Result,
    val onGrantAction: () -> Unit,
    val onHelp: () -> Unit,
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

        if (item.state.missingPermission.isEmpty()) {
            val isError = item.state.isAccessFaked
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isError) Icons.TwoTone.ErrorOutline else Icons.TwoTone.CheckCircle,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(
                        if (isError) R.string.setup_permission_error_label
                        else R.string.setup_permission_granted_label,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (item.state.isAccessFaked) {
            Text(
                text = stringResource(R.string.setup_inventory_invalid_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        if (!item.state.isComplete) {
            Button(
                onClick = item.onGrantAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (item.state.isAccessFaked) CommonR.string.general_open_system_settings_action
                        else CommonR.string.general_grant_access_action,
                    ),
                )
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
private fun InventorySetupCardPreview() {
    PreviewWrapper {
        InventorySetupCard(
            item = InventorySetupCardItem(
                state = InventorySetupModule.Result(
                    missingPermission = emptySet(),
                    isAccessFaked = true,
                    settingsIntent = Intent(),
                ),
                onGrantAction = {},
                onHelp = {},
            ),
        )
    }
}
