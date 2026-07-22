package eu.darken.sdmse.appcleaner.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.AppIconImage
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListViewModel
import eu.darken.sdmse.appcleaner.ui.preview.previewAppCleanerRow
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SelectableListRow
import eu.darken.sdmse.common.compose.SelectableListRowIconBox
import eu.darken.sdmse.common.compose.SystemAppChip
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.getSettingsIntent

private val TAG = logTag("AppCleaner", "List", "Row")

@Composable
fun AppCleanerListRow(
    modifier: Modifier = Modifier,
    row: AppCleanerListViewModel.Row,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailsClick: () -> Unit,
) {
    val context = LocalContext.current
    val junk = row.junk

    val itemsText = pluralStringResource(
        CommonR.plurals.result_x_items,
        junk.itemCount,
        junk.itemCount,
    )
    val sizeText = Formatter.formatShortFileSize(context, junk.size)

    SelectableListRow(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        SelectableListRowIconBox(
            onClick = if (selectionActive) onClick else onDetailsClick,
            onLongClick = {
                runCatching { context.startActivity(junk.pkg.getSettingsIntent(context)) }
                    .onFailure { log(TAG, WARN) { "Settings intent failed for ${junk.pkg}: $it" } }
            },
        ) {
            AppIconImage(
                pkg = junk.pkg,
                contentDescription = stringResource(CommonR.string.general_details_label),
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = junk.label.get(context),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = junk.pkg.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                if (junk.isSystemApp) SystemAppChip()
            }
        }
    }
}

@Preview2
@Composable
private fun AppCleanerListRowPreview() {
    PreviewWrapper {
        AppCleanerListRow(
            row = previewAppCleanerRow(),
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
private fun AppCleanerListRowSelectedPreview() {
    PreviewWrapper {
        AppCleanerListRow(
            row = previewAppCleanerRow(),
            selected = true,
            selectionActive = true,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}
