package eu.darken.sdmse.analyzer.ui.storage.content

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.ui.storage.content.ContentViewModel.Item
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.iconRes

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContentItemRow(
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
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val lookup = content.lookup
                if (lookup != null) {
                    FilePreviewImage(
                        lookup = lookup,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(content.type.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = primary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            when (content.type) {
                                FileType.DIRECTORY -> CommonR.string.file_type_directory
                                FileType.FILE -> CommonR.string.file_type_file
                                FileType.SYMBOLIC_LINK -> CommonR.string.file_type_symbolic_link
                                FileType.UNKNOWN -> CommonR.string.file_type_unknown
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.sizeRatio?.let { ratio ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }
    }
}
