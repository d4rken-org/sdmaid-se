package eu.darken.sdmse.deduplicator.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.deduplicator.R as DeduplicatorR

@Composable
fun PreviewDeletionDialog(
    mode: PreviewDeletionMode,
    onConfirm: (deleteAll: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPreviewClick: (PreviewOptions) -> Unit,
    onShowDetails: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var deleteAllChecked by rememberSaveable(mode::class, mode.allowDeleteAll) { mutableStateOf(false) }
    val previews = mode.previews
    val singlePreview = previews.singleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (singlePreview != null) {
                    FilePreviewImage(
                        lookup = singlePreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 192.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onPreviewClick(PreviewOptions(paths = listOf(singlePreview.lookedUp)))
                            },
                    )
                } else if (previews.isNotEmpty()) {
                    val allPaths = previews.map { it.lookedUp }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 192.dp)
                            .wrapContentHeight(),
                        contentPadding = PaddingValues(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(previews, key = { it.path.toString() }) { lookup ->
                            FilePreviewImage(
                                lookup = lookup,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onPreviewClick(
                                            PreviewOptions(
                                                paths = allPaths,
                                                position = previews.indexOf(lookup),
                                            )
                                        )
                                    },
                            )
                        }
                    }
                }

                if (mode.allowDeleteAll) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(DeduplicatorR.string.deduplicator_delete_all_toggle_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f),
                        )
                        Switch(
                            checked = deleteAllChecked,
                            onCheckedChange = { deleteAllChecked = it },
                        )
                    }
                }

                Text(
                    text = formatMessage(mode = mode, deleteAll = deleteAllChecked, context = context),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteAllChecked) }) {
                Text(stringResource(CommonR.string.general_delete_action))
            }
        },
        dismissButton = {
            Row {
                if (onShowDetails != null) {
                    TextButton(onClick = onShowDetails) {
                        Text(stringResource(CommonR.string.general_show_details_action))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            }
        },
    )
}

private fun formatMessage(
    mode: PreviewDeletionMode,
    deleteAll: Boolean,
    context: android.content.Context,
): String = when (mode) {
    is PreviewDeletionMode.All -> context.getString(
        DeduplicatorR.string.deduplicator_delete_confirmation_message,
    )

    is PreviewDeletionMode.Clusters -> singleOrMultipleSetMessage(mode.count, deleteAll, context)

    is PreviewDeletionMode.Groups -> singleOrMultipleSetMessage(mode.count, deleteAll, context)

    is PreviewDeletionMode.Duplicates -> when (mode.count) {
        1 -> context.getString(
            CommonR.string.general_delete_confirmation_message_x,
            mode.duplicates.single().lookup.userReadablePath.get(context),
        )

        else -> context.resources.getQuantityString(
            CommonR.plurals.general_delete_confirmation_message_selected_x_items,
            mode.count,
            mode.count,
        )
    }
}

private fun singleOrMultipleSetMessage(
    count: Int,
    deleteAll: Boolean,
    context: android.content.Context,
): String {
    val resId = when (count) {
        1 -> when (deleteAll) {
            true -> DeduplicatorR.string.deduplicator_delete_single_set_keep_none_confirmation_message
            false -> DeduplicatorR.string.deduplicator_delete_single_set_confirmation_message
        }

        else -> when (deleteAll) {
            true -> DeduplicatorR.string.deduplicator_delete_multiple_sets_keep_none_confirmation_message
            false -> DeduplicatorR.string.deduplicator_delete_multiple_sets_confirmation_message
        }
    }
    return context.getString(resId)
}
