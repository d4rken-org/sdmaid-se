package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.ui.storage.content.ContentViewModel.*
import eu.darken.sdmse.analyzer.ui.storage.preview.previewContentItem
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.R as CommonR

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
    val cardBorder = if (isSelected) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = cardColor,
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val lookup = content.lookup
            if (lookup != null) {
                FilePreviewImage(
                    lookup = lookup,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = content.type.icon,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (progressFraction != null) {
                        CircularProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                        .padding(2.dp),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun ContentItemTilePreview() {
    PreviewWrapper {
        ContentItemTile(
            item = previewTileItem(),
            isSelected = false,
            isSelectionMode = false,
        )
    }
}

@Preview2
@Composable
private fun ContentItemTileSelectedPreview() {
    PreviewWrapper {
        ContentItemTile(
            item = previewTileItem(),
            isSelected = true,
            isSelectionMode = true,
        )
    }
}

private fun previewTileItem() = Item(
    parent = previewContentItem(
        segments = arrayOf("storage", "emulated", "0", "DCIM"),
        type = FileType.FILE,
        size = 15L * 1024 * 1024,
        withLookup = false,
    ),
    content = previewContentItem(
        segments = arrayOf("storage", "emulated", "0", "DCIM", "vacation.jpg"),
        type = FileType.FILE,
        size = 3L * 1024 * 1024,
        withLookup = false,
    ),
    sizeRatio = 0.4f,
)
