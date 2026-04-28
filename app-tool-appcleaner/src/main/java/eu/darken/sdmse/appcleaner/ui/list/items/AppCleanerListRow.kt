package eu.darken.sdmse.appcleaner.ui.list.items

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListViewModel
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.getSettingsIntent

private val TAG = logTag("AppCleaner", "List", "Row")

@OptIn(ExperimentalFoundationApi::class)
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
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    val itemsText = pluralStringResource(
        CommonR.plurals.result_x_items,
        junk.itemCount,
        junk.itemCount,
    )
    val sizeText = Formatter.formatShortFileSize(context, junk.size)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(junk.pkg).build(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        runCatching { context.startActivity(junk.pkg.getSettingsIntent(context)) }
                            .onFailure { log(TAG, WARN) { "Settings intent failed for ${junk.pkg}: $it" } }
                    },
                ),
        )
        Spacer(Modifier.width(16.dp))
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
                if (junk.isSystemApp) {
                    Icon(
                        painter = painterResource(CommonR.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onDetailsClick,
                enabled = !selectionActive,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.FolderOpen,
                    contentDescription = stringResource(CommonR.string.general_details_label),
                )
            }
        }
    }
}
