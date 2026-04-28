package eu.darken.sdmse.setup.saf

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
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
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import java.io.File
import eu.darken.sdmse.common.R as CommonR

data class SAFSetupCardItem(
    override val state: SAFSetupModule.Result,
    val onPathClicked: (SAFSetupModule.Result.PathAccess) -> Unit,
    val onHelp: () -> Unit,
) : SetupCardItem

@Composable
internal fun SAFSetupCard(
    item: SAFSetupCardItem,
    modifier: Modifier = Modifier,
) {
    SetupCardContainer(
        icon = Icons.Outlined.FolderOpen,
        title = stringResource(R.string.setup_saf_card_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        Text(
            text = stringResource(R.string.setup_saf_card_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            item.state.paths.forEach { pathAccess ->
                SafPathRow(
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
            text = stringResource(R.string.setup_saf_card_body2),
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
internal fun SafPathRow(
    pathAccess: SAFSetupModule.Result.PathAccess,
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
private fun SAFSetupCardPreview() {
    PreviewWrapper {
        SAFSetupCard(
            item = SAFSetupCardItem(
                state = SAFSetupModule.Result(
                    paths = listOf(
                        SAFSetupModule.Result.PathAccess(
                            label = "Public storage".toCaString(),
                            safPath = SAFPath.build(
                                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A"),
                            ),
                            localPath = LocalPath.build(File("/storage/emulated/0")),
                            uriPermission = null,
                            grantIntent = Intent(),
                        ),
                        SAFSetupModule.Result.PathAccess(
                            label = "Public app data".toCaString(),
                            safPath = SAFPath.build(
                                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata"),
                            ),
                            localPath = LocalPath.build(File("/storage/emulated/0/Android/data")),
                            uriPermission = null,
                            grantIntent = Intent(),
                        ),
                    ),
                ),
                onPathClicked = {},
                onHelp = {},
            ),
        )
    }
}
