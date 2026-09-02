package eu.darken.sdmse.squeezer.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.core.PriorCompression
import eu.darken.sdmse.squeezer.ui.preview.previewCompressibleImage
import eu.darken.sdmse.squeezer.ui.preview.previewCompressibleVideo

internal object SqueezerListGridCardTags {
    const val MARKER_ROW = "squeezer_grid_marker_row"
}

@Composable
internal fun SqueezerListGridCard(
    modifier: Modifier = Modifier,
    media: CompressibleMedia,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPreviewTap: () -> Unit,
) {
    val context = LocalContext.current
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    Card(
        modifier = modifier
            .padding(4.dp)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = CardDefaults.shape,
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                FilePreviewImage(
                    lookup = media.lookup,
                    modifier = Modifier.fillMaxSize(),
                )
                if (media is CompressibleVideo) {
                    Icon(
                        imageVector = Icons.TwoTone.PlayArrow,
                        contentDescription = stringResource(R.string.squeezer_type_video_title),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                shape = CircleShape,
                            )
                            .padding(6.dp),
                    )
                }
                SqueezeMarkerChips(
                    media = media,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
                FilledTonalIconButton(
                    onClick = onPreviewTap,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Search,
                        contentDescription = stringResource(R.string.squeezer_compare_action),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = Formatter.formatShortFileSize(context, media.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
        }
    }
}

/**
 * The same chips the linear row shows, laid over the preview. They carry an opaque container and
 * their own label, so they stay legible against an arbitrary photo. FlowRow so a pair of long
 * translated labels wraps to a second line rather than clipping off the card. Nothing is composed
 * when no marker applies.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SqueezeMarkerChips(
    modifier: Modifier = Modifier,
    media: CompressibleMedia,
) {
    val markers = media.squeezeMarkers()
    if (!markers.any) return

    FlowRow(
        modifier = modifier.testTag(SqueezerListGridCardTags.MARKER_ROW),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SqueezeMarkerChips(markers)
    }
}

@Preview2
@Composable
private fun SqueezerListGridCardPreview() {
    PreviewWrapper {
        Row {
            SqueezerListGridCard(
                modifier = Modifier.weight(1f),
                media = previewCompressibleImage(),
                isSelected = false,
                onTap = {},
                onLongPress = {},
                onPreviewTap = {},
            )
            SqueezerListGridCard(
                modifier = Modifier.weight(1f),
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
private fun SqueezerListGridCardMarkedPreview() {
    PreviewWrapper {
        Row {
            SqueezerListGridCard(
                modifier = Modifier.weight(1f),
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
            SqueezerListGridCard(
                modifier = Modifier.weight(1f),
                media = previewCompressibleVideo(priorCompression = PriorCompression.NO_SAVINGS),
                isSelected = false,
                onTap = {},
                onLongPress = {},
                onPreviewTap = {},
            )
        }
    }
}
