package eu.darken.sdmse.deduplicator.ui.settings.arbiter.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium

@Composable
fun ArbiterCriteriumRow(
    modifier: Modifier = Modifier,
    criterium: ArbiterCriterium,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val titleRes = criterium.titleRes()
    val descriptionRes = criterium.descriptionRes()
    val icon = criterium.icon()

    val modeText: String = when (criterium) {
        is ArbiterCriterium.PreferredPath -> {
            if (criterium.keepPreferPaths.isEmpty()) {
                stringResource(R.string.deduplicator_arbiter_configure_paths_action)
            } else {
                criterium.keepPreferPaths.joinToString("\n") { it.userReadablePath.get(context) }
            }
        }

        else -> criterium.criteriumMode()?.labelRes?.let { stringResource(it) }.orEmpty()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (modeText.isNotEmpty()) {
                Text(
                    text = modeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        dragHandle()
    }
}

@Composable
fun ArbiterConfigDragHandle(modifier: Modifier = Modifier) {
    IconButton(onClick = {}, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ArbiterCriterium.titleRes(): Int = when (this) {
    is ArbiterCriterium.DuplicateType -> R.string.deduplicator_arbiter_criterium_duplicate_type_title
    is ArbiterCriterium.MediaProvider -> R.string.deduplicator_arbiter_criterium_media_provider_title
    is ArbiterCriterium.Location -> R.string.deduplicator_arbiter_criterium_location_title
    is ArbiterCriterium.Nesting -> R.string.deduplicator_arbiter_criterium_nesting_title
    is ArbiterCriterium.Modified -> R.string.deduplicator_arbiter_criterium_modified_title
    is ArbiterCriterium.Size -> R.string.deduplicator_arbiter_criterium_size_title
    is ArbiterCriterium.PreferredPath -> R.string.deduplicator_arbiter_criterium_preferred_path_title
}

private fun ArbiterCriterium.descriptionRes(): Int = when (this) {
    is ArbiterCriterium.DuplicateType -> R.string.deduplicator_arbiter_criterium_duplicate_type_description
    is ArbiterCriterium.MediaProvider -> R.string.deduplicator_arbiter_criterium_media_provider_description
    is ArbiterCriterium.Location -> R.string.deduplicator_arbiter_criterium_location_description
    is ArbiterCriterium.Nesting -> R.string.deduplicator_arbiter_criterium_nesting_description
    is ArbiterCriterium.Modified -> R.string.deduplicator_arbiter_criterium_modified_description
    is ArbiterCriterium.Size -> R.string.deduplicator_arbiter_criterium_size_description
    is ArbiterCriterium.PreferredPath -> R.string.deduplicator_arbiter_criterium_preferred_path_description
}

private fun ArbiterCriterium.icon(): ImageVector = when (this) {
    is ArbiterCriterium.DuplicateType -> SdmIcons.CodeEqualBox
    is ArbiterCriterium.PreferredPath -> Icons.Outlined.Folder
    is ArbiterCriterium.MediaProvider -> Icons.Outlined.PlayCircleOutline
    is ArbiterCriterium.Location -> Icons.Outlined.SdCard
    is ArbiterCriterium.Nesting -> Icons.Outlined.AccountTree
    is ArbiterCriterium.Modified -> Icons.Outlined.History
    is ArbiterCriterium.Size -> Icons.Outlined.MonitorWeight
}
