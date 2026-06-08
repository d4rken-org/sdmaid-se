package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.ui.storage.content.ContentViewModel.Item
import eu.darken.sdmse.analyzer.ui.storage.preview.previewContentItem
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FileListThumbnail
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.labelRes

@Composable
internal fun ContentItemRow(
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

    val selectionColor = MaterialTheme.colorScheme.secondaryContainer
    val barColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ratio = item.sizeRatio?.coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .drawBehind {
                    if (isSelected) {
                        drawRect(color = selectionColor)
                    }
                    if (ratio != null && ratio > 0f) {
                        val barWidth = size.width * ratio
                        val x = if (isRtl) size.width - barWidth else 0f
                        drawRect(
                            color = barColor,
                            topLeft = Offset(x, 0f),
                            size = Size(width = barWidth, height = size.height),
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lookup = content.lookup
            if (lookup != null && content.type == FileType.FILE && (content.size ?: 0L) > 0L) {
                FileListThumbnail(
                    lookup = lookup,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = content.type.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(content.type.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .widthIn(max = 100.dp),
            )
        }
        HorizontalDivider()
    }
}

@Preview2
@Composable
private fun ContentItemRowPreview() {
    PreviewWrapper {
        ContentItemRow(
            item = Item(
                parent = null,
                content = previewContentItem(
                    segments = arrayOf("storage", "emulated", "0", "Download", "report.pdf"),
                    type = FileType.FILE,
                    size = 8L * 1024 * 1024,
                    withLookup = false,
                ),
                sizeRatio = 0.5f,
            ),
            isSelected = false,
            isSelectionMode = false,
        )
    }
}

@Preview2
@Composable
private fun ContentItemRowSelectedFullBarPreview() {
    PreviewWrapper {
        ContentItemRow(
            item = Item(
                parent = null,
                content = previewContentItem(
                    segments = arrayOf("storage", "emulated", "0", "Movies"),
                    type = FileType.DIRECTORY,
                    size = 64L * 1024 * 1024,
                    children = setOf(
                        previewContentItem(segments = arrayOf("storage", "emulated", "0", "Movies", "a.mp4"), withLookup = false),
                        previewContentItem(segments = arrayOf("storage", "emulated", "0", "Movies", "b.mp4"), withLookup = false),
                    ),
                    withLookup = false,
                ),
                sizeRatio = 1f,
            ),
            isSelected = true,
            isSelectionMode = true,
        )
    }
}

@Preview2
@Composable
private fun ContentItemRowLongNamePreview() {
    PreviewWrapper {
        ContentItemRow(
            item = Item(
                parent = null,
                content = previewContentItem(
                    segments = arrayOf(
                        "storage", "emulated", "0",
                        "eu.darken.sdmse.test.sd-area-access-local-3bffb8bc-8481-492b-acdf-65f700dc15ae",
                    ),
                    type = FileType.FILE,
                    size = 0L,
                    withLookup = false,
                ),
                sizeRatio = 0f,
            ),
            isSelected = false,
            isSelectionMode = false,
        )
    }
}
