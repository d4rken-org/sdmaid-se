package eu.darken.sdmse.deduplicator.ui.details.cluster

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.ApproximatelyEqualBox
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.common.compose.SdmInfoChip
import eu.darken.sdmse.deduplicator.ui.details.tour.DeduplicatorDetailsTour
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumGroup
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import eu.darken.sdmse.deduplicator.ui.preview.previewMediaGroup
import eu.darken.sdmse.deduplicator.ui.preview.previewPHashGroup
import kotlin.math.roundToLong

@Composable
internal fun ClusterContent(
    cluster: Duplicate.Cluster,
    isDirectoryView: Boolean,
    collapsed: Set<DirectoryGroup.Id>,
    selection: Set<Duplicate.Id>,
    onSelectionToggle: (Duplicate.Id) -> Unit,
    onSelectionLongPress: (Duplicate.Id) -> Unit,
    onCollapseToggle: (DirectoryGroup.Id) -> Unit,
    onClusterDelete: () -> Unit,
    onClusterExclude: () -> Unit,
    onGroupDelete: (Duplicate.Group.Id) -> Unit,
    onGroupView: (Duplicate.Group, position: Int) -> Unit,
    onDuplicateDelete: (Duplicate.Id) -> Unit,
    onDuplicatePreview: (Duplicate) -> Unit,
    onDirectoryDeleteAll: (DirectoryGroup) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    applyClusterHeaderTourTarget: Boolean = false,
    tourDeleteMarkTarget: Duplicate.Id? = null,
) {
    val elements = remember(cluster, isDirectoryView, collapsed) {
        buildClusterElements(cluster = cluster, isDirectoryView = isDirectoryView, collapsed = collapsed)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(elements, key = { it.key }) { element ->
            when (element) {
                is ClusterElement.ClusterHeader -> ClusterHeaderRow(
                    cluster = element.cluster,
                    onDelete = onClusterDelete,
                    onExclude = onClusterExclude,
                    tourModifier = if (applyClusterHeaderTourTarget) {
                        Modifier.guidedTourTarget(DeduplicatorDetailsTour.CLUSTER_HEADER_TARGET)
                    } else {
                        Modifier
                    },
                )

                is ClusterElement.DirectoryHeader -> DirectoryHeaderRow(
                    group = element.group,
                    isCollapsed = element.isCollapsed,
                    onCollapseToggle = { onCollapseToggle(element.group.identifier) },
                    onDeleteAll = { onDirectoryDeleteAll(element.group) },
                )

                is ClusterElement.GroupHeader -> GroupHeaderCard(
                    group = element.group,
                    willBeDeleted = element.willBeDeleted,
                    onDelete = { onGroupDelete(element.group.identifier) },
                    onView = { onGroupView(element.group, 0) },
                )

                is ClusterElement.ChecksumDuplicateRow -> ChecksumFileRow(
                    duplicate = element.duplicate,
                    willBeDeleted = element.willBeDeleted,
                    selected = selection.contains(element.duplicate.identifier),
                    selectionActive = selection.isNotEmpty(),
                    onClick = {
                        if (selection.isNotEmpty()) onSelectionToggle(element.duplicate.identifier)
                        else onDuplicateDelete(element.duplicate.identifier)
                    },
                    onLongClick = { onSelectionLongPress(element.duplicate.identifier) },
                    deleteMarkModifier = if (element.duplicate.identifier == tourDeleteMarkTarget) {
                        Modifier.guidedTourTarget(DeduplicatorDetailsTour.ROW_DELETE_MARK_TARGET)
                    } else {
                        Modifier
                    },
                )

                is ClusterElement.PHashDuplicateRow -> ImageFileRow(
                    duplicate = element.duplicate,
                    similarity = element.duplicate.similarity,
                    matchType = null,
                    willBeDeleted = element.willBeDeleted,
                    selected = selection.contains(element.duplicate.identifier),
                    selectionActive = selection.isNotEmpty(),
                    onClick = {
                        if (selection.isNotEmpty()) onSelectionToggle(element.duplicate.identifier)
                        else onDuplicateDelete(element.duplicate.identifier)
                    },
                    onLongClick = { onSelectionLongPress(element.duplicate.identifier) },
                    onPreviewClick = { onDuplicatePreview(element.duplicate) },
                    deleteMarkModifier = if (element.duplicate.identifier == tourDeleteMarkTarget) {
                        Modifier.guidedTourTarget(DeduplicatorDetailsTour.ROW_DELETE_MARK_TARGET)
                    } else {
                        Modifier
                    },
                )

                is ClusterElement.MediaDuplicateRow -> {
                    val matchType = mediaMatchTypeLabel(element.duplicate)
                    ImageFileRow(
                        duplicate = element.duplicate,
                        similarity = element.duplicate.similarity,
                        matchType = matchType,
                        willBeDeleted = element.willBeDeleted,
                        selected = selection.contains(element.duplicate.identifier),
                        selectionActive = selection.isNotEmpty(),
                        onClick = {
                            if (selection.isNotEmpty()) onSelectionToggle(element.duplicate.identifier)
                            else onDuplicateDelete(element.duplicate.identifier)
                        },
                        onLongClick = { onSelectionLongPress(element.duplicate.identifier) },
                        onPreviewClick = { onDuplicatePreview(element.duplicate) },
                        deleteMarkModifier = if (element.duplicate.identifier == tourDeleteMarkTarget) {
                            Modifier.guidedTourTarget(DeduplicatorDetailsTour.ROW_DELETE_MARK_TARGET)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClusterHeaderRow(
    cluster: Duplicate.Cluster,
    onDelete: () -> Unit,
    onExclude: () -> Unit,
    tourModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(tourModifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val totalSize = Formatter.formatShortFileSize(context, cluster.totalSize)
            val freeable = context.resources.getQuantityString(
                CommonR.plurals.x_space_can_be_freed,
                1,
                Formatter.formatShortFileSize(context, cluster.redundantSize),
            )
            Text(
                text = stringResource(DeduplicatorR.string.deduplicator_occupied_space_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$totalSize ($freeable)",
                style = MaterialTheme.typography.titleMedium,
            )
            val checksumCount = cluster.groups.filterIsInstance<ChecksumDuplicate.Group>().sumOf { it.count }
            val similarCount = cluster.groups.filterIsInstance<PHashDuplicate.Group>().sumOf { it.count } +
                cluster.groups.filterIsInstance<MediaDuplicate.Group>().sumOf { it.count }
            if (checksumCount > 0 || similarCount > 0) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (checksumCount > 0) {
                        SdmInfoChip(
                            icon = SdmIcons.CodeEqualBox,
                            label = "${stringResource(DeduplicatorR.string.deduplicator_matches_exact_label)}: ${
                                pluralStringResource(CommonR.plurals.result_x_files, checksumCount, checksumCount)
                            }",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (similarCount > 0) {
                        SdmInfoChip(
                            icon = SdmIcons.ApproximatelyEqualBox,
                            label = "${stringResource(DeduplicatorR.string.deduplicator_matches_similar_label)}: ${
                                pluralStringResource(CommonR.plurals.result_x_files, similarCount, similarCount)
                            }",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onExclude,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(SdmIcons.ShieldAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_exclude_action))
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.TwoTone.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            }
        }
    }
}

@Composable
private fun DirectoryHeaderRow(
    group: DirectoryGroup,
    isCollapsed: Boolean,
    onCollapseToggle: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .combinedClickableSafe(onClick = onDeleteAll),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Folder,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.parentSegments.joinSegments(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${Formatter.formatFileSize(context, group.totalSize)}" +
                        " (${context.resources.getQuantityString(CommonR.plurals.result_x_items, group.count, group.count)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCollapseToggle) {
                Icon(
                    imageVector = if (isCollapsed) Icons.TwoTone.ExpandMore else Icons.TwoTone.ExpandLess,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun GroupHeaderCard(
    group: Duplicate.Group,
    willBeDeleted: Boolean,
    onDelete: () -> Unit,
    onView: () -> Unit,
) {
    val context = LocalContext.current
    val (methodIcon, methodLabelRes) = when (group.type) {
        Duplicate.Type.CHECKSUM ->
            SdmIcons.CodeEqualBox to DeduplicatorR.string.deduplicator_detection_method_checksum_title

        Duplicate.Type.PHASH ->
            SdmIcons.ApproximatelyEqualBox to DeduplicatorR.string.deduplicator_detection_method_phash_title

        Duplicate.Type.MEDIA ->
            Icons.TwoTone.GraphicEq to DeduplicatorR.string.deduplicator_detection_method_media_title
    }
    val overlayColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val overlayContent = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickableSafe(onClick = onView),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            FilePreviewImage(
                lookup = group.previewFile,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(overlayColor)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = methodIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = overlayContent,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(DeduplicatorR.string.deduplicator_detection_method_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = overlayContent,
                    )
                    Text(
                        text = stringResource(methodLabelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = overlayContent,
                    )
                }
                IconButton(onClick = onView) {
                    Icon(
                        imageVector = Icons.TwoTone.Visibility,
                        contentDescription = stringResource(CommonR.string.general_view_action),
                        tint = overlayContent,
                    )
                }
                if (willBeDeleted) {
                    Icon(
                        imageVector = Icons.TwoTone.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = stringResource(CommonR.string.general_delete_action),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(overlayColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(CommonR.string.general_count_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = overlayContent,
                    )
                    Text(
                        text = pluralStringResource(CommonR.plurals.result_x_files, group.count, group.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = overlayContent,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(DeduplicatorR.string.deduplicator_average_size_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = overlayContent,
                    )
                    Text(
                        text = Formatter.formatFileSize(context, group.averageSize.roundToLong()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = overlayContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecksumFileRow(
    duplicate: ChecksumDuplicate,
    willBeDeleted: Boolean,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    deleteMarkModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val name = duplicate.path.userReadableName.get(context)
    val parentPath = duplicate.path.userReadablePath.get(context).removeSuffix(name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (parentPath.isNotEmpty()) {
                    Text(
                        text = parentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (willBeDeleted) {
                Icon(
                    imageVector = Icons.TwoTone.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = deleteMarkModifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ImageFileRow(
    duplicate: Duplicate,
    similarity: Double,
    matchType: String?,
    willBeDeleted: Boolean,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreviewClick: () -> Unit,
    deleteMarkModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val name = duplicate.path.userReadableName.get(context)
    val parentPath = duplicate.path.userReadablePath.get(context).removeSuffix(name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilePreviewImage(
            lookup = duplicate.lookup,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickableSafe(onClick = onPreviewClick),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (parentPath.isNotEmpty()) {
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val similarityText = String.format("%.2f%%", similarity * 100)
            val secondaryText = if (matchType != null) "$similarityText ($matchType)" else similarityText
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${stringResource(DeduplicatorR.string.deduplicator_file_size_label)}: ${
                Formatter.formatShortFileSize(context, duplicate.size)
            }",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (willBeDeleted) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.TwoTone.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = deleteMarkModifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun mediaMatchTypeLabel(duplicate: MediaDuplicate): String? = when {
    duplicate.audioHash != null && duplicate.frameHashes.isNotEmpty() ->
        stringResource(DeduplicatorR.string.deduplicator_media_match_audio_visual)

    duplicate.audioHash != null -> stringResource(DeduplicatorR.string.deduplicator_media_match_audio)
    duplicate.frameHashes.isNotEmpty() -> stringResource(DeduplicatorR.string.deduplicator_media_match_visual)
    else -> null
}

private fun Modifier.combinedClickableSafe(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = {})

@Preview2
@Composable
private fun ClusterContentChecksumPreview() {
    PreviewWrapper {
        ClusterContent(
            cluster = previewCluster(groups = setOf(previewChecksumGroup())),
            isDirectoryView = false,
            collapsed = emptySet(),
            selection = emptySet(),
            onSelectionToggle = {},
            onSelectionLongPress = {},
            onCollapseToggle = {},
            onClusterDelete = {},
            onClusterExclude = {},
            onGroupDelete = {},
            onGroupView = { _, _ -> },
            onDuplicateDelete = {},
            onDuplicatePreview = {},
            onDirectoryDeleteAll = {},
        )
    }
}

@Preview2
@Composable
private fun ClusterContentImagePreview() {
    PreviewWrapper {
        ClusterContent(
            cluster = previewCluster(groups = setOf(previewPHashGroup(), previewMediaGroup())),
            isDirectoryView = false,
            collapsed = emptySet(),
            selection = emptySet(),
            onSelectionToggle = {},
            onSelectionLongPress = {},
            onCollapseToggle = {},
            onClusterDelete = {},
            onClusterExclude = {},
            onGroupDelete = {},
            onGroupView = { _, _ -> },
            onDuplicateDelete = {},
            onDuplicatePreview = {},
            onDirectoryDeleteAll = {},
        )
    }
}

@Preview2
@Composable
private fun ClusterContentDirectoryPreview() {
    PreviewWrapper {
        ClusterContent(
            cluster = previewCluster(groups = setOf(previewChecksumGroup())),
            isDirectoryView = true,
            collapsed = emptySet(),
            selection = emptySet(),
            onSelectionToggle = {},
            onSelectionLongPress = {},
            onCollapseToggle = {},
            onClusterDelete = {},
            onClusterExclude = {},
            onGroupDelete = {},
            onGroupView = { _, _ -> },
            onDuplicateDelete = {},
            onDuplicatePreview = {},
            onDirectoryDeleteAll = {},
        )
    }
}
