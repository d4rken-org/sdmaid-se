package eu.darken.sdmse.deduplicator.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.ApproximatelyEqualBox
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListViewModel.DeduplicatorListRow
import eu.darken.sdmse.deduplicator.ui.preview.previewDeduplicatorListRow
import eu.darken.sdmse.deduplicator.ui.preview.previewDeduplicatorListRowAllTypes

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
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        // Square tile: the preview fills the card and the caption is overlaid on a scrim at the bottom,
        // mirroring the Analyzer's content grid so every grid tile shares the same square footprint.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            // Tapping the thumbnail deletes the cluster's duplicates (confirm dialog); long-press selects.
            FilePreviewImage(
                lookup = cluster.previewFile,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_cluster_preview_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = deleteLabel,
                        onClick = onThumbnailClick,
                        onLongClick = onLongClick,
                    ),
            )
            // Small corner-cutout button (top-start): open the full-screen preview. Long-press still selects.
            PreviewOverlayButton(
                onClick = onPreviewButtonClick,
                onLongClick = onLongClick,
                modifier = Modifier.align(Alignment.TopStart),
            )
            // Caption overlaid at the bottom: tap = open details; long-press selects.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = detailsLabel,
                        onClick = onCaptionClick,
                        onLongClick = onLongClick,
                    )
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(
                        DeduplicatorR.string.deduplicator_list_grid_freeable_x,
                        Formatter.formatShortFileSize(context, row.freeableSize),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
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
}

@Composable
private fun PreviewOverlayButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(DeduplicatorR.string.deduplicator_action_open_preview)
    // 48dp transparent box keeps an accessible tap target; the visible control is a small 28dp cutout
    // notched into the corner. The Card's Surface clips the outer top-start corner to the card radius,
    // so only the inner (bottom-end) corner needs rounding to read as a cutout.
    Box(
        modifier = modifier
            .size(48.dp)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(bottomEnd = 12.dp))
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Fullscreen,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Grid cards show detection types as compact icons (no labels) to keep the card to two short lines.
 * All icons share the caption's text color so they stay legible on the scrim; the glyph shapes still
 * distinguish the match type.
 */
@Composable
private fun MatchTypeIcons(types: Set<Duplicate.Type>) {
    val tint = Color.White.copy(alpha = 0.85f)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (Duplicate.Type.CHECKSUM in types) {
            Icon(
                imageVector = SdmIcons.CodeEqualBox,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_detection_method_checksum_title),
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        if (Duplicate.Type.PHASH in types) {
            Icon(
                imageVector = SdmIcons.ApproximatelyEqualBox,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_detection_method_phash_title),
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        if (Duplicate.Type.MEDIA in types) {
            Icon(
                imageVector = Icons.TwoTone.GraphicEq,
                contentDescription = stringResource(DeduplicatorR.string.deduplicator_detection_method_media_title),
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Preview2
@Composable
private fun DeduplicatorGridRowPreview() {
    PreviewWrapper {
        // Constrain to a grid-tile width so the preview reflects how it renders in the LazyVerticalGrid.
        Box(modifier = Modifier.width(180.dp)) {
            DeduplicatorGridRow(
                row = previewDeduplicatorListRow(),
                selected = false,
                onThumbnailClick = {},
                onCaptionClick = {},
                onPreviewButtonClick = {},
                onLongClick = {},
            )
        }
    }
}

@Preview2
@Composable
private fun DeduplicatorGridRowSelectedPreview() {
    PreviewWrapper {
        Box(modifier = Modifier.width(180.dp)) {
            DeduplicatorGridRow(
                row = previewDeduplicatorListRow(),
                selected = true,
                onThumbnailClick = {},
                onCaptionClick = {},
                onPreviewButtonClick = {},
                onLongClick = {},
            )
        }
    }
}

@Preview2
@Composable
private fun DeduplicatorGridRowAllTypesNarrowPreview() {
    PreviewWrapper {
        // Narrow tile (3-column layout) with all three match types: the tightest caption layout.
        Box(modifier = Modifier.width(115.dp)) {
            DeduplicatorGridRow(
                row = previewDeduplicatorListRowAllTypes(),
                selected = false,
                onThumbnailClick = {},
                onCaptionClick = {},
                onPreviewButtonClick = {},
                onLongClick = {},
            )
        }
    }
}
