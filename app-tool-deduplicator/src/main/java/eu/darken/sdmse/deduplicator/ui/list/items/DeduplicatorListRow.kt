package eu.darken.sdmse.deduplicator.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.ApproximatelyEqualBox
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListViewModel.DeduplicatorListRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeduplicatorLinearRow(
    row: DeduplicatorListRow,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onDuplicateClick: (Duplicate) -> Unit,
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilePreviewImage(
                lookup = cluster.previewFile,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = if (selectionActive) onClick else onPreviewClick,
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
            }
            Spacer(Modifier.width(8.dp))
            MatchTypeChips(types = cluster.types)
        }

        cluster.groups
            .flatMap { it.duplicates }
            .forEach { dupe ->
                val willBeDeleted = dupe.identifier in row.deleteTargetIds
                DuplicateSubRow(
                    duplicate = dupe,
                    willBeDeleted = willBeDeleted,
                    onClick = { onDuplicateClick(dupe) },
                    onPreviewClick = { onDuplicatePreviewClick(dupe) },
                )
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeduplicatorGridRow(
    row: DeduplicatorListRow,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cluster = row.cluster

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            FilePreviewImage(
                lookup = cluster.previewFile,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .combinedClickable(
                        onClick = if (selectionActive) onClick else onPreviewClick,
                        onLongClick = onLongClick,
                    ),
            )
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val totalSize = Formatter.formatShortFileSize(context, cluster.totalSize)
                    val freeable = context.resources.getQuantityString(
                        CommonR.plurals.x_space_can_be_freed,
                        1,
                        Formatter.formatShortFileSize(context, cluster.redundantSize),
                    )
                    Text(
                        text = "$totalSize ($freeable)",
                        style = MaterialTheme.typography.titleSmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                MatchTypeChips(types = cluster.types)
            }
        }
    }
}

@Composable
private fun MatchTypeChips(types: Set<Duplicate.Type>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (Duplicate.Type.CHECKSUM in types) {
            Icon(
                imageVector = SdmIcons.CodeEqualBox,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (Duplicate.Type.PHASH in types) {
            Icon(
                imageVector = SdmIcons.ApproximatelyEqualBox,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        if (Duplicate.Type.MEDIA in types) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateSubRow(
    duplicate: Duplicate,
    willBeDeleted: Boolean,
    onClick: () -> Unit,
    onPreviewClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = {})
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (duplicate.type != Duplicate.Type.CHECKSUM) {
            FilePreviewImage(
                lookup = duplicate.lookup,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onPreviewClick, onLongClick = {}),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = duplicate.path.userReadableName.get(context),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = duplicate.path.userReadablePath.get(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = Formatter.formatShortFileSize(context, duplicate.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (willBeDeleted) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
