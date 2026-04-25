package eu.darken.sdmse.systemcleaner.ui.details.page

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.asComposable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.systemcleaner.core.FilterContent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun FilterContentPage(
    filterContent: FilterContent,
    selection: Set<APath>,
    onSelectionChange: (Set<APath>) -> Unit,
    onDeleteFilterRequest: () -> Unit,
    onExcludeFilterRequest: () -> Unit,
    onFileTap: (FilterContentElement.FileRow) -> Unit,
    onPreviewFile: (APath) -> Unit,
    modifier: Modifier = Modifier,
) {
    val elements = remember(filterContent) { buildFilterContentElements(filterContent) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = "header") {
            FilterContentHeaderCard(
                filterContent = filterContent,
                onDeleteAll = onDeleteFilterRequest,
                onExclude = onExcludeFilterRequest,
            )
        }
        items(elements, key = { it.match.path.path }) { element ->
            val isSelected = selection.contains(element.match.path)
            FilterContentFileRow(
                element = element,
                selected = isSelected,
                selectionActive = selection.isNotEmpty(),
                onClick = {
                    if (selection.isNotEmpty()) {
                        val updated = if (isSelected) {
                            selection - element.match.path
                        } else {
                            selection + element.match.path
                        }
                        onSelectionChange(updated)
                    } else {
                        onFileTap(element)
                    }
                },
                onLongClick = { onSelectionChange(selection + element.match.path) },
                onPreviewClick = if (element.showThumbnailPreview) {
                    { onPreviewFile(element.match.path) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun FilterContentHeaderCard(
    filterContent: FilterContent,
    onDeleteAll: () -> Unit,
    onExclude: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = filterContent.icon.asComposable(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = filterContent.label.get(context),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = filterContent.description.get(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(
                        CommonR.plurals.result_x_items,
                        filterContent.items.size,
                        filterContent.items.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatter.formatFileSize(context, filterContent.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onExclude,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_exclude_action))
                }
                Button(
                    onClick = onDeleteAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterContentFileRow(
    element: FilterContentElement.FileRow,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreviewClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    val match = element.match
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbnailModifier = Modifier.size(40.dp)
        if (onPreviewClick != null && !selectionActive) {
            FilePreviewImage(
                lookup = match.lookup,
                modifier = thumbnailModifier.combinedClickable(onClick = onPreviewClick),
            )
        } else {
            FilePreviewImage(
                lookup = match.lookup,
                modifier = thumbnailModifier,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.lookup.userReadablePath.get(context),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (element.showDate) {
                val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
                Text(
                    text = match.lookup.modifiedAt.toSystemTimezone().format(formatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (match.lookup.fileType == FileType.FILE) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = Formatter.formatShortFileSize(context, match.expectedGain),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
