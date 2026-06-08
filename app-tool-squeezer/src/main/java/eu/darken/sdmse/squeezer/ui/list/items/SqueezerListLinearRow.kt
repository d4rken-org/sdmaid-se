package eu.darken.sdmse.squeezer.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FileListThumbnail
import eu.darken.sdmse.common.compose.SelectableListRow
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.replaceLast
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.ui.preview.previewCompressibleImage
import eu.darken.sdmse.squeezer.ui.preview.previewCompressibleVideo

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
                .size(56.dp)
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
            Text(
                text = stringResource(
                    R.string.squeezer_current_size_format,
                    Formatter.formatShortFileSize(context, media.size),
                ),
                style = MaterialTheme.typography.labelSmall,
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
