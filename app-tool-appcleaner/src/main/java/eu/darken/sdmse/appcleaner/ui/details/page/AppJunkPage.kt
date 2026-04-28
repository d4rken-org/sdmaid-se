package eu.darken.sdmse.appcleaner.ui.details.page

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.ui.descriptionRes
import eu.darken.sdmse.appcleaner.ui.icon
import eu.darken.sdmse.appcleaner.ui.labelRes
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.localized
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.pkgs.getSettingsIntent

private val TAG = logTag("AppCleaner", "Details", "Page")

@Composable
internal fun AppJunkPage(
    junk: AppJunk,
    collapsed: Set<ExpendablesFilterIdentifier>,
    selection: Set<APath>,
    selectionActive: Boolean,
    onSelectionChange: (Set<APath>) -> Unit,
    onDeleteJunk: () -> Unit,
    onExcludeJunk: () -> Unit,
    onDeleteInaccessible: () -> Unit,
    onDeleteCategory: (ExpendablesFilterIdentifier) -> Unit,
    onDeleteFile: (ExpendablesFilterIdentifier, APath) -> Unit,
    onToggleCollapse: (ExpendablesFilterIdentifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val elements = remember(junk, collapsed) { buildAppJunkElements(junk, collapsed) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        elements.forEach { element ->
            when (element) {
                AppJunkElement.Header -> item(key = "header") {
                    AppJunkPageHeaderCard(
                        junk = junk,
                        onDeleteJunk = onDeleteJunk,
                        onExcludeJunk = onExcludeJunk,
                    )
                }

                is AppJunkElement.Inaccessible -> item(key = "inaccessible") {
                    AppJunkInaccessibleRow(
                        cache = element.cache,
                        onClick = onDeleteInaccessible,
                    )
                }

                is AppJunkElement.CategoryHeader -> item(key = "cat-${element.category}") {
                    AppJunkCategoryCard(
                        category = element.category,
                        matchCount = element.matches.size,
                        totalSize = element.totalSize,
                        isCollapsed = element.isCollapsed,
                        onClick = { onDeleteCategory(element.category) },
                        onCollapseToggle = { onToggleCollapse(element.category) },
                    )
                }

                is AppJunkElement.FileRow -> item(key = "${element.category}-${element.match.path.path}") {
                    val isSelected = selection.contains(element.match.path)
                    AppJunkFileRow(
                        match = element.match,
                        selected = isSelected,
                        onClick = {
                            if (selectionActive) {
                                val updated = if (isSelected) {
                                    selection - element.match.path
                                } else {
                                    selection + element.match.path
                                }
                                onSelectionChange(updated)
                            } else {
                                onDeleteFile(element.category, element.match.path)
                            }
                        },
                        onLongClick = { onSelectionChange(selection + element.match.path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppJunkPageHeaderCard(
    junk: AppJunk,
    onDeleteJunk: () -> Unit,
    onExcludeJunk: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconWithSettingsLongPress(junk = junk)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = junk.label.get(context),
                        style = MaterialTheme.typography.titleMedium,
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
                }
                if (junk.isSystemApp) {
                    Icon(
                        painter = painterResource(CommonR.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            junk.userProfile?.let { profile ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.getHumanLabel().get(context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            junk.acsError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.localized(context).description.get(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(
                        CommonR.plurals.result_x_items,
                        junk.itemCount,
                        junk.itemCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatter.formatFileSize(context, junk.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onExcludeJunk,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.TwoTone.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_exclude_action))
                }
                Button(
                    onClick = onDeleteJunk,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.TwoTone.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIconWithSettingsLongPress(junk: AppJunk) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(junk.pkg).build(),
        contentDescription = null,
        modifier = Modifier
            .size(48.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    runCatching { context.startActivity(junk.pkg.getSettingsIntent(context)) }
                        .onFailure { log(TAG, WARN) { "Settings intent failed for ${junk.pkg}: $it" } }
                },
            ),
    )
}

@Composable
private fun AppJunkInaccessibleRow(
    cache: InaccessibleCache,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.appcleaner_item_caches_inaccessible_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.appcleaner_item_caches_inaccessible_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = Formatter.formatShortFileSize(context, cache.totalSize),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AppJunkCategoryCard(
    category: ExpendablesFilterIdentifier,
    matchCount: Int,
    totalSize: Long,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    onCollapseToggle: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(category.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = pluralStringResource(
                            CommonR.plurals.result_x_items,
                            matchCount,
                            matchCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = Formatter.formatShortFileSize(context, totalSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onCollapseToggle) {
                Icon(
                    imageVector = if (isCollapsed) Icons.TwoTone.ExpandMore else Icons.TwoTone.ExpandLess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppJunkFileRow(
    match: ExpendablesFilter.Match,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilePreviewImage(
            lookup = match.lookup,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.lookup.userReadablePath.get(context),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
