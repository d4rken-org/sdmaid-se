package eu.darken.sdmse.deduplicator.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FileListThumbnail
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.SdmInfoChip
import eu.darken.sdmse.common.compose.icons.ApproximatelyEqualBox
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListViewModel.DeduplicatorListRow

@Composable
internal fun DeduplicatorLinearRow(
    row: DeduplicatorListRow,
    selected: Boolean,
    selectionActive: Boolean,
    selectedDupes: Set<Duplicate.Id>,
    onHeaderClick: () -> Unit,
    onLongClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    onDuplicateClick: (Duplicate) -> Unit,
    onDuplicateLongClick: (Duplicate) -> Unit,
    onDuplicatePreviewClick: (Duplicate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cluster = row.cluster

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val deleteLabel = stringResource(DeduplicatorR.string.deduplicator_action_delete_set)
    val previewLabel = stringResource(DeduplicatorR.string.deduplicator_action_open_preview)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        // Header tap = delete this cluster's duplicates (confirm dialog); the thumbnail previews; long-press selects.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = deleteLabel,
                    onClick = onHeaderClick,
                    onLongClick = onLongClick,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilePreviewImage(
                lookup = cluster.previewFile,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_cluster_preview_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = previewLabel,
                        onClick = onThumbnailClick,
                        onLongClick = onLongClick,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val totalSize = Formatter.formatShortFileSize(context, cluster.totalSize)
                val freeable = context.resources.getQuantityString(
                    CommonR.plurals.x_space_can_be_freed,
                    1,
                    Formatter.formatShortFileSize(context, cluster.redundantSize),
                )
                Text(
                    text = "$totalSize ($freeable)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(
                        CommonR.plurals.result_x_items,
                        cluster.count,
                        cluster.count,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cluster.types.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MatchTypeChipRow(types = cluster.types)
                }
            }
        }

        cluster.groups
            .flatMap { it.duplicates }
            .forEach { dupe ->
                val willBeDeleted = dupe.identifier in row.deleteTargetIds
                DuplicateSubRow(
                    duplicate = dupe,
                    willBeDeleted = willBeDeleted,
                    selected = dupe.identifier in selectedDupes,
                    selectionActive = selectionActive,
                    onClick = { onDuplicateClick(dupe) },
                    onLongClick = { onDuplicateLongClick(dupe) },
                    onPreviewClick = { onDuplicatePreviewClick(dupe) },
                )
            }
    }
}

@Composable
internal fun DeduplicatorGridRow(
    row: DeduplicatorListRow,
    selected: Boolean,
    onThumbnailClick: () -> Unit,
    onCaptionClick: () -> Unit,
    onPreviewButtonClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cluster = row.cluster

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val deleteLabel = stringResource(DeduplicatorR.string.deduplicator_action_delete_set)
    val detailsLabel = stringResource(CommonR.string.general_show_details_action)

    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Tapping the thumbnail deletes the cluster's duplicates (confirm dialog); long-press selects.
            FilePreviewImage(
                lookup = cluster.previewFile,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_cluster_preview_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = deleteLabel,
                        onClick = onThumbnailClick,
                        onLongClick = onLongClick,
                    ),
            )
            // Small overlay button (top-left): open the full-screen preview. Long-press still selects.
            PreviewOverlayButton(
                onClick = onPreviewButtonClick,
                onLongClick = onLongClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
        }
        // Caption tap = open details; long-press selects.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = detailsLabel,
                    onClick = onCaptionClick,
                    onLongClick = onLongClick,
                )
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(
                    DeduplicatorR.string.deduplicator_list_grid_freeable_x,
                    Formatter.formatShortFileSize(context, row.freeableSize),
                ),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        CommonR.plurals.result_x_items,
                        cluster.count,
                        cluster.count,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (cluster.types.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    MatchTypeIcons(types = cluster.types)
                }
            }
        }
    }
}

@Composable
private fun PreviewOverlayButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(DeduplicatorR.string.deduplicator_action_open_preview)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Fullscreen,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Grid cards show detection types as compact icons (no labels) to keep the card to two short lines. */
@Composable
private fun MatchTypeIcons(types: Set<Duplicate.Type>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (Duplicate.Type.CHECKSUM in types) {
            Icon(
                imageVector = SdmIcons.CodeEqualBox,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_detection_method_checksum_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (Duplicate.Type.PHASH in types) {
            Icon(
                imageVector = SdmIcons.ApproximatelyEqualBox,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_detection_method_phash_title),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (Duplicate.Type.MEDIA in types) {
            Icon(
                imageVector = Icons.TwoTone.GraphicEq,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_detection_method_media_title),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchTypeChipRow(types: Set<Duplicate.Type>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (Duplicate.Type.CHECKSUM in types) {
            SdmInfoChip(
                icon = SdmIcons.CodeEqualBox,
                label = stringResource(DeduplicatorR.string.deduplicator_detection_method_checksum_title),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (Duplicate.Type.PHASH in types) {
            SdmInfoChip(
                icon = SdmIcons.ApproximatelyEqualBox,
                label = stringResource(DeduplicatorR.string.deduplicator_detection_method_phash_title),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (Duplicate.Type.MEDIA in types) {
            SdmInfoChip(
                icon = Icons.TwoTone.GraphicEq,
                label = stringResource(DeduplicatorR.string.deduplicator_detection_method_media_title),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun DuplicateSubRow(
    duplicate: Duplicate,
    willBeDeleted: Boolean,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreviewClick: () -> Unit,
) {
    val context = LocalContext.current
    val name = duplicate.path.userReadableName.get(context)
    val parentPath = duplicate.path.userReadablePath.get(context).removeSuffix(name)
    val rowModifier = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (duplicate.type != Duplicate.Type.CHECKSUM) {
            FileListThumbnail(
                lookup = duplicate.lookup,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = if (selectionActive) onClick else onPreviewClick,
                        onLongClick = onLongClick,
                    ),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (parentPath.isNotEmpty()) {
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
            // Delete-target marker sits BEFORE the size so the size stays end-aligned across all rows.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (willBeDeleted) {
                    Icon(
                        imageVector = Icons.TwoTone.DeleteSweep,
                        contentDescription = stringResource(DeduplicatorR.string.deduplicator_marked_for_deletion),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = Formatter.formatShortFileSize(context, duplicate.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
