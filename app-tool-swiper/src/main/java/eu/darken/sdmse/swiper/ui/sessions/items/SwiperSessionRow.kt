package eu.darken.sdmse.swiper.ui.sessions.items

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.FileTypeCategory
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SortOrder
import eu.darken.sdmse.swiper.core.Swiper

@Composable
fun SwiperSessionRow(
    modifier: Modifier = Modifier,
    sessionWithStats: Swiper.SessionWithStats,
    position: Int,
    isScanning: Boolean,
    isCancelling: Boolean,
    isRefreshing: Boolean,
    isRisky: Boolean = false,
    onScan: () -> Unit,
    onContinue: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onCancel: () -> Unit,
    onFilter: () -> Unit,
    onSortOrder: () -> Unit,
) {
    val context = LocalContext.current
    val session = sessionWithStats.session

    val isScanned = sessionWithStats.isScanned
    val noMatchingFiles = isScanned && session.totalItems == 0
    val canContinue = isScanned && session.totalItems > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = session.label
                                ?: stringResource(R.string.swiper_session_default_label, position),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.TwoTone.Edit,
                                contentDescription = stringResource(R.string.swiper_session_rename_title),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val createdRel = DateUtils.getRelativeTimeSpanString(
                        session.createdAt.toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    )
                    val modifiedRel = DateUtils.getRelativeTimeSpanString(
                        session.lastModifiedAt.toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    )
                    Text(
                        text = stringResource(R.string.swiper_session_timestamps, createdRel, modifiedRel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.TwoTone.Close,
                        contentDescription = stringResource(R.string.swiper_session_remove_action),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.swiper_session_paths_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.sourcePaths.forEach { path ->
                Text(
                    text = path.userReadablePath.get(context),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val filter = session.fileTypeFilter
            if (isRisky) {
                Spacer(Modifier.height(8.dp))
                // Filter narrows the scope to a known shape — still a whole-storage walk, but the
                // user has already opted into a smaller bite. Tone the chip down from error to
                // tertiary so a focused scan with `images-only` doesn't scream the same red as an
                // unfiltered "everything on /storage/emulated/0".
                val chipColors = if (filter.isEmpty) {
                    AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.swiper_session_risky_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    colors = chipColors,
                )
            }

            if (!filter.isEmpty) {
                Spacer(Modifier.height(8.dp))
                val parts = buildList {
                    filter.categories.sortedBy { it.ordinal }.forEach { category ->
                        val name = when (category) {
                            FileTypeCategory.IMAGES -> stringResource(R.string.swiper_file_type_category_images)
                            FileTypeCategory.VIDEOS -> stringResource(R.string.swiper_file_type_category_videos)
                            FileTypeCategory.AUDIO -> stringResource(R.string.swiper_file_type_category_audio)
                            FileTypeCategory.DOCUMENTS -> stringResource(R.string.swiper_file_type_category_documents)
                            FileTypeCategory.ARCHIVES -> stringResource(R.string.swiper_file_type_category_archives)
                        }
                        add(name)
                    }
                    filter.customExtensions.sorted().forEach { add(".$it") }
                }
                Text(
                    text = stringResource(R.string.swiper_file_type_filter_summary, parts.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (noMatchingFiles) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.swiper_session_no_matching_files),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isScanned && !noMatchingFiles) {
                Spacer(Modifier.height(12.dp))
                val decidedItems = sessionWithStats.keepCount + sessionWithStats.deleteCount
                val total = session.totalItems
                val percent = if (total > 0) decidedItems * 100 / total else 100
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "$percent%", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.swiper_session_status_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.swiper_session_status_to_keep,
                            sessionWithStats.keepCount,
                            sessionWithStats.keepCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.swiper_session_status_to_delete,
                            sessionWithStats.deleteCount,
                            sessionWithStats.deleteCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.swiper_session_status_undecided,
                            sessionWithStats.undecidedCount,
                            sessionWithStats.undecidedCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            val showFilterSort = !isScanning && !isRefreshing &&
                (session.state == SessionState.CREATED || noMatchingFiles)
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showFilterSort) {
                    val filterActive = !filter.isEmpty
                    AssistChip(
                        onClick = onFilter,
                        leadingIcon = { Icon(Icons.TwoTone.FilterList, contentDescription = null) },
                        label = { Text(stringResource(R.string.swiper_file_type_filter_title)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    val sortActive = session.sortOrder != SortOrder.DEFAULT
                    AssistChip(
                        onClick = onSortOrder,
                        leadingIcon = { Icon(Icons.AutoMirrored.TwoTone.Sort, contentDescription = null) },
                        label = { Text(stringResource(CommonR.string.general_sort_by_title)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (sortActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = if (sortActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(contentAlignment = Alignment.Center) {
                    if (isScanning || isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                    SessionActionButton(
                        isScanning = isScanning,
                        isCancelling = isCancelling,
                        isRefreshing = isRefreshing,
                        canContinue = canContinue,
                        hasStarted = sessionWithStats.keepCount + sessionWithStats.deleteCount > 0,
                        onScan = onScan,
                        onContinue = onContinue,
                        onCancel = onCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionActionButton(
    isScanning: Boolean,
    isCancelling: Boolean,
    isRefreshing: Boolean,
    canContinue: Boolean,
    hasStarted: Boolean,
    onScan: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    when {
        isScanning -> TextButton(onClick = onCancel, enabled = !isCancelling) {
            Icon(Icons.TwoTone.Cancel, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(CommonR.string.general_cancel_action))
        }

        isRefreshing -> TextButton(onClick = {}, enabled = false) {
            Text(stringResource(CommonR.string.general_progress_loading))
        }

        canContinue -> Button(onClick = onContinue) {
            Icon(Icons.TwoTone.Swipe, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (hasStarted) stringResource(R.string.swiper_continue_action)
                else stringResource(R.string.swiper_start_action),
            )
        }

        else -> Button(onClick = onScan) {
            Icon(Icons.TwoTone.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(CommonR.string.general_scan_action))
        }
    }
}
