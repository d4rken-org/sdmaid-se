package eu.darken.sdmse.corpsefinder.ui.details.content

import android.text.format.Formatter
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
import androidx.compose.material.icons.twotone.DeleteSweep
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.corpsefinder.R as CorpseR
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.icon
import eu.darken.sdmse.corpsefinder.ui.labelRes
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse

@Composable
internal fun CorpseContent(
    corpse: Corpse,
    selection: Set<APath>,
    onSelectionChange: (Set<APath>) -> Unit,
    onDeleteCorpseRequest: () -> Unit,
    onExcludeRequest: () -> Unit,
    onFileTap: (APathLookup<*>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedContent = remember(corpse.identifier, corpse.content) {
        corpse.content.sortedByDescending { it.size }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = "header") {
            CorpseHeaderCard(
                corpse = corpse,
                onDeleteAll = onDeleteCorpseRequest,
                onExclude = onExcludeRequest,
            )
        }
        items(sortedContent, key = { it.lookedUp.toString() }) { lookup ->
            val isSelected = selection.contains(lookup.lookedUp)
            CorpseFileRow(
                corpse = corpse,
                lookup = lookup,
                selected = isSelected,
                selectionActive = selection.isNotEmpty(),
                onClick = {
                    if (selection.isNotEmpty()) {
                        val updated = if (isSelected) selection - lookup.lookedUp else selection + lookup.lookedUp
                        onSelectionChange(updated)
                    } else {
                        onFileTap(lookup)
                    }
                },
                onLongClick = { onSelectionChange(selection + lookup.lookedUp) },
            )
        }
    }
}

@Composable
private fun CorpseHeaderCard(
    corpse: Corpse,
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
            Text(
                text = stringResource(CommonR.string.general_path_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = corpse.lookup.userReadablePath.get(context),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = corpse.filterType.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(CommonR.string.general_type_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = stringResource(corpse.filterType.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(CommonR.string.general_size_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = corpse.lookup.fileType.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = Formatter.formatFileSize(context, corpse.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(CorpseR.string.corpsefinder_owners_label),
                style = MaterialTheme.typography.labelMedium,
            )
            val ownersText = if (corpse.ownerInfo.owners.isNotEmpty()) {
                corpse.ownerInfo.owners.joinToString("\n") { it.pkgId.name }
            } else {
                stringResource(CorpseR.string.corpsefinder_owner_unknown)
            }
            Text(text = ownersText, style = MaterialTheme.typography.bodyMedium)

            val hintRes = when (corpse.riskLevel) {
                RiskLevel.NORMAL -> null
                RiskLevel.KEEPER -> CorpseR.string.corpsefinder_corpse_hint_keeper
                RiskLevel.COMMON -> CorpseR.string.corpsefinder_corpse_hint_common
            }
            if (hintRes != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(CommonR.string.general_hints_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (corpse.riskLevel) {
                        RiskLevel.KEEPER -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onExclude,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(SdmIcons.ShieldAdd, contentDescription = null)
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
                    Icon(Icons.TwoTone.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            }
        }
    }
}

@Composable
private fun CorpseFileRow(
    corpse: Corpse,
    lookup: APathLookup<*>,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val relative = lookup.lookedUp.removePrefix(corpse.lookup).joinSegments("/")
    val sizeText = Formatter.formatShortFileSize(context, lookup.size)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilePreviewImage(
            lookup = lookup,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = relative.ifEmpty { lookup.name },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (lookup.fileType == eu.darken.sdmse.common.files.FileType.FILE) {
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
private fun CorpseContentPreview() {
    PreviewWrapper {
        CorpseContent(
            corpse = previewCorpse(),
            selection = emptySet(),
            onSelectionChange = {},
            onDeleteCorpseRequest = {},
            onExcludeRequest = {},
            onFileTap = {},
        )
    }
}

@Preview2
@Composable
private fun CorpseContentKeeperPreview() {
    PreviewWrapper {
        CorpseContent(
            corpse = previewCorpse(riskLevel = RiskLevel.KEEPER),
            selection = emptySet(),
            onSelectionChange = {},
            onDeleteCorpseRequest = {},
            onExcludeRequest = {},
            onFileTap = {},
        )
    }
}
