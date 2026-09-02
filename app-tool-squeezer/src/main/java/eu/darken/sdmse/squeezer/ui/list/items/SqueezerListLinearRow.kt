package eu.darken.sdmse.squeezer.ui.list.items

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.HdrOn
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.MotionPhotosOn
import androidx.compose.material.icons.twotone.PhotoSizeSelectSmall
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FileListThumbnail
import eu.darken.sdmse.common.compose.SdmInfoChip
import eu.darken.sdmse.common.compose.SelectableListRow
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.replaceLast
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.core.PriorCompression
import eu.darken.sdmse.squeezer.ui.preview.previewCompressibleImage
import eu.darken.sdmse.squeezer.ui.preview.previewCompressibleVideo

internal object SqueezerListLinearRowTags {
    const val MARKER_ROW = "squeezer_linear_marker_row"
}

// Markers trail three lines of file detail in the row and sit over the preview in the grid, so they
// run tighter than a standalone SdmInfoChip. Shared by both layouts and the legend dialog.
private val MARKER_CHIP_PADDING = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
private val MARKER_CHIP_ICON_SIZE = 12.dp

@Composable
internal fun SqueezerListLinearRow(
    modifier: Modifier = Modifier,
    media: CompressibleMedia,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPreviewTap: () -> Unit,
) {
    val context = LocalContext.current

    SelectableListRow(
        modifier = modifier,
        selected = isSelected,
        onClick = onTap,
        onLongClick = onLongPress,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onPreviewTap,
                    onLongClick = onLongPress,
                ),
            contentAlignment = Alignment.Center,
        ) {
            FileListThumbnail(lookup = media.lookup, modifier = Modifier.fillMaxSize())
            if (media is CompressibleVideo) {
                Icon(
                    imageVector = Icons.TwoTone.PlayArrow,
                    contentDescription = stringResource(R.string.squeezer_type_video_title),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = CircleShape,
                        )
                        .padding(4.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.lookup.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val pathText = media.lookup.userReadablePath
                .get(context)
                .replaceLast(media.lookup.name, "")
            Text(
                text = pathText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.squeezer_current_size_format,
                        Formatter.formatShortFileSize(context, media.size),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(8.dp))
                val savings = media.estimatedSavings
                Text(
                    text = if (savings != null && savings > 0) {
                        stringResource(
                            R.string.squeezer_estimated_savings_format,
                            Formatter.formatShortFileSize(context, savings),
                        )
                    } else {
                        stringResource(R.string.squeezer_no_savings_expected)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SqueezeChipRow(media = media)
        }
    }
}

/**
 * Chips mark what is different about this file. An ordinary re-encode has none, and nothing is
 * composed then so those rows keep their height.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SqueezeChipRow(media: CompressibleMedia) {
    val markers = media.squeezeMarkers()
    if (!markers.any) return

    FlowRow(
        modifier = Modifier
            .padding(top = 2.dp)
            .testTag(SqueezerListLinearRowTags.MARKER_ROW),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SqueezeMarkerChips(markers)
    }
}

/** Which markers a scan result earns. Shared by the list row and the grid card. */
internal data class SqueezeMarkers(
    val compressedBefore: Boolean,
    val hdrDepth: Boolean,
    val motionPhoto: Boolean,
    val downscaled: Boolean,
) {
    val any: Boolean get() = compressedBefore || hdrDepth || motionPhoto || downscaled
}

internal fun CompressibleMedia.squeezeMarkers(): SqueezeMarkers {
    val image = this as? CompressibleImage
    return SqueezeMarkers(
        compressedBefore = priorCompression == PriorCompression.COMPRESSED,
        hdrDepth = image?.hasLossyAux == true,
        motionPhoto = image?.hasMotionVideo == true,
        downscaled = image?.willDownscale == true,
    )
}

/** Emits one chip per set marker into the caller's row; the info chip first, the losses after. */
@Composable
internal fun SqueezeMarkerChips(markers: SqueezeMarkers) {
    if (markers.compressedBefore) CompressedBeforeChip()
    if (markers.hdrDepth) HdrDepthChip()
    if (markers.motionPhoto) MotionPhotoChip()
    if (markers.downscaled) DownscaledChip()
}

@Composable
internal fun CompressedBeforeChip() = SdmInfoChip(
    icon = Icons.TwoTone.History,
    label = stringResource(R.string.squeezer_chip_compressed_before),
    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    iconSize = MARKER_CHIP_ICON_SIZE,
    contentPadding = MARKER_CHIP_PADDING,
)

/** Warning styling: this photo loses data compression can't reproduce. */
@Composable
internal fun HdrDepthChip() = SdmInfoChip(
    icon = Icons.TwoTone.HdrOn,
    label = stringResource(R.string.squeezer_chip_hdr_depth),
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    iconSize = MARKER_CHIP_ICON_SIZE,
    contentPadding = MARKER_CHIP_PADDING,
)

/** Warning styling: the embedded video clip does not survive a re-encode. */
@Composable
internal fun MotionPhotoChip() = SdmInfoChip(
    icon = Icons.TwoTone.MotionPhotosOn,
    label = stringResource(R.string.squeezer_chip_motion_photo),
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    iconSize = MARKER_CHIP_ICON_SIZE,
    contentPadding = MARKER_CHIP_PADDING,
)

/** Warning styling: the re-encode also halves this image's resolution. */
@Composable
internal fun DownscaledChip() = SdmInfoChip(
    icon = Icons.TwoTone.PhotoSizeSelectSmall,
    label = stringResource(R.string.squeezer_chip_downscaled),
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    iconSize = MARKER_CHIP_ICON_SIZE,
    contentPadding = MARKER_CHIP_PADDING,
)

@Preview2
@Composable
private fun SqueezerListLinearRowPreview() {
    PreviewWrapper {
        Column {
            SqueezerListLinearRow(
                media = previewCompressibleImage(),
                isSelected = false,
                onTap = {},
                onLongPress = {},
                onPreviewTap = {},
            )
            SqueezerListLinearRow(
                media = previewCompressibleVideo(),
                isSelected = true,
                onTap = {},
                onLongPress = {},
                onPreviewTap = {},
            )
        }
    }
}

@Preview2
@Composable
private fun SqueezerListLinearRowMarkedPreview() {
    PreviewWrapper {
        Column {
            SqueezerListLinearRow(
                media = previewCompressibleImage(
                    priorCompression = PriorCompression.COMPRESSED,
                    hasLossyAux = true,
                    hasMotionVideo = true,
                    willDownscale = true,
                ),
                isSelected = false,
                onTap = {},
                onLongPress = {},
                onPreviewTap = {},
            )
            SqueezerListLinearRow(
                media = previewCompressibleVideo(priorCompression = PriorCompression.NO_SAVINGS),
                isSelected = false,
                onTap = {},
                onLongPress = {},
                onPreviewTap = {},
            )
        }
    }
}
