package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.ui.storage.content.ContentViewModel.Item
import eu.darken.sdmse.analyzer.ui.storage.preview.previewContentItem
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType

@Composable
internal fun ContentItemTile(
    modifier: Modifier = Modifier,
    item: Item,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val context = LocalContext.current
    val content = item.content
    val parent = item.parent

    val primary: String = if (parent != null) {
        content.path.segments.drop(parent.path.segments.size).single()
    } else {
        content.label.get(context)
    }

    val sizeText = content.size?.let { Formatter.formatShortFileSize(context, it) } ?: "?"
    val secondary: String = when {
        content.inaccessible -> sizeText
        content.type == FileType.DIRECTORY -> if (content.size != null) {
            val itemsFormatted = pluralStringResource(
                CommonR.plurals.result_x_items,
                content.children.size,
                content.children.size,
            )
            "$sizeText ($itemsFormatted)"
        } else "?"
        else -> sizeText
    }

    val parentSize = parent?.size
    val progressFraction: Float? = if (parentSize != null && parentSize > 0L) {
        ((content.size ?: 0L).toFloat() / parentSize.toFloat()).coerceIn(0f, 1f)
    } else null

    val cardColor = if (isSelected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = cardColor,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val lookup = content.lookup
                if (lookup != null) {
                    FilePreviewImage(
                        lookup = lookup,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = content.type.icon,
                        contentDescription = null,
                    )
                }
            }
            Text(
                text = primary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (progressFraction != null) {
                    CircularProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun ContentItemTilePreview() {
    PreviewWrapper {
        ContentItemTile(
            item = Item(
                parent = null,
                content = previewContentItem(
                    segments = arrayOf("storage", "emulated", "0", "DCIM", "vacation.jpg"),
                    type = FileType.FILE,
                    size = 3L * 1024 * 1024,
                    withLookup = false,
                ),
                sizeRatio = 0.4f,
            ),
            isSelected = false,
            isSelectionMode = false,
        )
    }
}
