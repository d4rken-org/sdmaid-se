package eu.darken.sdmse.systemcleaner.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SelectableListRow
import eu.darken.sdmse.common.compose.SelectableListRowIconBox
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListViewModel
import eu.darken.sdmse.systemcleaner.ui.preview.previewSystemCleanerRow

@Composable
fun SystemCleanerRow(
    modifier: Modifier = Modifier,
    row: SystemCleanerListViewModel.Row,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailsClick: () -> Unit,
) {
    val context = LocalContext.current
    val content = row.content

    val itemsText = pluralStringResource(
        CommonR.plurals.result_x_items,
        content.items.size,
        content.items.size,
    )
    val sizeText = Formatter.formatShortFileSize(context, content.size)

    SelectableListRow(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        SelectableListRowIconBox(
            onClick = if (selectionActive) null else onDetailsClick,
            onLongClick = if (selectionActive) null else onLongClick,
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = stringResource(CommonR.string.general_details_label),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.label.get(context),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.description.get(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = itemsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SystemCleanerRowPreview() {
    PreviewWrapper {
        SystemCleanerRow(
            row = previewSystemCleanerRow(),
            selected = false,
            selectionActive = false,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}

@Preview2
@Composable
private fun SystemCleanerRowSelectedPreview() {
    PreviewWrapper {
        SystemCleanerRow(
            row = previewSystemCleanerRow(),
            selected = true,
            selectionActive = true,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}
