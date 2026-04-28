package eu.darken.sdmse.squeezer.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.replaceLast
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage

@Composable
internal fun SqueezerListLinearRow(
    modifier: Modifier = Modifier,
    image: CompressibleImage,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPreviewTap: () -> Unit,
) {
    val context = LocalContext.current
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilePreviewImage(
            lookup = image.lookup,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onPreviewTap,
                    onLongClick = onLongPress,
                ),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = image.lookup.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val pathText = image.lookup.userReadablePath
                .get(context)
                .replaceLast(image.lookup.name, "")
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
                    Formatter.formatShortFileSize(context, image.size),
                ),
                style = MaterialTheme.typography.labelSmall,
            )
            val savings = image.estimatedSavings
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
