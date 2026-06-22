package eu.darken.sdmse.common.picker.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.areas.label
import eu.darken.sdmse.common.coil.FileListThumbnail
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.labelRes
import eu.darken.sdmse.common.picker.PickerViewModel
import eu.darken.sdmse.common.ui.R as UiR

@Composable
fun PickerItemRow(
    modifier: Modifier = Modifier,
    row: PickerViewModel.PickerRow,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val item = row.item
    val context = LocalContext.current
    val isRoot = item.parent == null

    val primary = if (isRoot) item.lookup.lookedUp.path else item.lookup.name
    val secondary = item.dataArea.type.label.get(context)
    val tertiaryRes = when (item.lookup.fileType) {
        FileType.DIRECTORY, FileType.FILE, FileType.SYMBOLIC_LINK, FileType.UNKNOWN -> item.lookup.fileType.labelRes
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Directories/area-roots open on a body tap; a file has nothing to open into, so its
            // body tap toggles selection instead (only the checkbox would otherwise select it).
            .clickable(onClick = { if (row.navigable) onClick() else onToggleSelect() })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRoot) {
            // Area roots use a distinct home-folder icon (legacy parity).
            Icon(
                painter = painterResource(UiR.drawable.ic_folder_home_24),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Non-root items show a Coil file preview when available, falling back to a full-size
            // type icon on transparent for folders and files Coil can't preview.
            FileListThumbnail(
                lookup = item.lookup,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Box(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(primary, style = MaterialTheme.typography.bodyLarge)
            Text(
                secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(tertiaryRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.selectable) {
            // Enlarge the tap target around the checkbox so taps landing slightly beside it still
            // toggle selection instead of falling through to the row's open-folder click. The
            // checkbox itself is passive (onCheckedChange = null) — the Box owns the click.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center,
            ) {
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = null,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun PickerItemRowPreview() {
    PreviewWrapper {
        Column {
            PickerItemRow(
                row = previewPickerRootRow(),
                onClick = {},
                onToggleSelect = {},
            )
            PickerItemRow(
                row = previewPickerChildRow(selected = false),
                onClick = {},
                onToggleSelect = {},
            )
            PickerItemRow(
                row = previewPickerChildRow(selected = true),
                onClick = {},
                onToggleSelect = {},
            )
            PickerItemRow(
                row = previewPickerFileChildRow(),
                onClick = {},
                onToggleSelect = {},
            )
        }
    }
}
