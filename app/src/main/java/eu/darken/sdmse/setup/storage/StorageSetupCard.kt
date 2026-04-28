package eu.darken.sdmse.setup.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.asComposable
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import java.io.File
import eu.darken.sdmse.common.R as CommonR

data class StorageSetupCardItem(
    override val state: StorageSetupModule.Result,
    val onPathClicked: (StorageSetupModule.Result.PathAccess) -> Unit,
    val onHelp: () -> Unit,
) : SetupCardItem

@Composable
internal fun StorageSetupCard(
    item: StorageSetupCardItem,
    modifier: Modifier = Modifier,
) {
    SetupCardContainer(
        icon = Icons.Outlined.SdStorage,
        title = stringResource(R.string.setup_manage_storage_card_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        Text(
            text = stringResource(R.string.setup_manage_storage_card_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            item.state.paths.forEach { pathAccess ->
                StoragePathRow(
                    pathAccess = pathAccess,
                    onClick = { item.onPathClicked(pathAccess) },
                )
            }
        }
        if (!item.state.isComplete) {
            val firstUngranted = item.state.paths.firstOrNull { !it.hasAccess }
            Button(
                onClick = { firstUngranted?.let(item.onPathClicked) },
                enabled = firstUngranted != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(CommonR.string.general_grant_access_action))
            }
        }
        Text(
            text = stringResource(R.string.setup_manage_storage_card_body2),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
internal fun StoragePathRow(
    pathAccess: StorageSetupModule.Result.PathAccess,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val granted = pathAccess.hasAccess
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = pathAccess.label.asComposable(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pathAccess.localPath.userReadablePath.asComposable(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (granted) {
                Text(
                    text = stringResource(R.string.setup_permission_granted_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun StorageSetupCardPreview() {
    PreviewWrapper {
        StorageSetupCard(
            item = StorageSetupCardItem(
                state = StorageSetupModule.Result(
                    paths = listOf(
                        StorageSetupModule.Result.PathAccess(
                            label = "Public storage".toCaString(),
                            localPath = LocalPath.build(File("/storage/emulated/0")),
                            hasAccess = true,
                        ),
                        StorageSetupModule.Result.PathAccess(
                            label = "SD card".toCaString(),
                            localPath = LocalPath.build(File("/storage/ABCD-EF12")),
                            hasAccess = false,
                        ),
                    ),
                    missingPermission = emptySet(),
                ),
                onPathClicked = {},
                onHelp = {},
            ),
        )
    }
}
