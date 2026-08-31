package eu.darken.sdmse.systemcleaner.ui.details.page

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FileListThumbnail
import eu.darken.sdmse.common.coil.canAttemptFilePreview
import eu.darken.sdmse.common.compose.layout.SdmWholeScopeActions
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.selection.SelectionState
import eu.darken.sdmse.common.compose.selection.rememberSelectionState
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun FilterContentPage(
    modifier: Modifier = Modifier,
    filterContent: FilterContent,
    selection: SelectionState<APath>,
    selectionEnabled: Boolean,
    wholeScopeActionsEnabled: Boolean,
    onDeleteFilterRequest: () -> Unit,
    onExcludeFilterRequest: () -> Unit,
    onFileTap: (FilterContentElement.FileRow) -> Unit,
    onPreviewFile: (APath) -> Unit,
) {
    val elements = remember(filterContent) { buildFilterContentElements(filterContent) }
    val selectionActive = selectionEnabled && selection.isActive

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = "header") {
            FilterContentHeaderCard(
                filterContent = filterContent,
                wholeScopeActionsEnabled = wholeScopeActionsEnabled,
                onDeleteAll = onDeleteFilterRequest,
                onExclude = onExcludeFilterRequest,
            )
        }
        items(elements, key = { it.match.path.path }) { element ->
            val isSelected = selectionEnabled && selection.isSelected(element.match.path)
            FilterContentFileRow(
                element = element,
                selected = isSelected,
                selectionActive = selectionActive,
                onClick = {
                    if (selectionEnabled && selection.isActive) {
                        selection.toggle(element.match.path)
                    } else {
                        onFileTap(element)
                    }
                },
                onLongClick = { if (selectionEnabled) selection.select(element.match.path) },
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
    wholeScopeActionsEnabled: Boolean,
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
                    imageVector = filterContent.icon,
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
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(CommonR.string.general_count_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = pluralStringResource(
                            CommonR.plurals.result_x_items,
                            filterContent.items.size,
                            filterContent.items.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(CommonR.string.general_size_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = Formatter.formatFileSize(context, filterContent.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SdmWholeScopeActions(
                enabled = wholeScopeActionsEnabled,
                onExclude = onExclude,
                onDelete = onDeleteAll,
            )
        }
    }
}

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

    val sizeText = Formatter.formatShortFileSize(context, match.expectedGain)
    val dateText = if (element.showDate) {
        val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
        match.lookup.modifiedAt.toSystemTimezone().format(formatter)
    } else {
        null
    }
    val supporting = listOfNotNull(sizeText, dateText).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbnailModifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
        // Only make the thumbnail a preview tap-target when a preview can be attempted (a non-empty
        // file); folders and empty items are never preview targets.
        if (onPreviewClick != null && !selectionActive && match.lookup.canAttemptFilePreview()) {
            FileListThumbnail(
                lookup = match.lookup,
                modifier = thumbnailModifier.combinedClickable(onClick = onPreviewClick),
            )
        } else {
            FileListThumbnail(
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
            if (supporting.isNotEmpty()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun FilterContentPagePreview() {
    PreviewWrapper {
        FilterContentPage(
            filterContent = previewFilterContent(),
            selection = rememberSelectionState(),
            selectionEnabled = true,
            wholeScopeActionsEnabled = true,
            onDeleteFilterRequest = {},
            onExcludeFilterRequest = {},
            onFileTap = {},
            onPreviewFile = {},
        )
    }
}
