package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.ui.storage.content.ContentViewModel.Item
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.icon

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContentItemTile(
    item: Item,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
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

    val secondary: String = content.size?.let { Formatter.formatShortFileSize(context, it) } ?: "?"

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
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
