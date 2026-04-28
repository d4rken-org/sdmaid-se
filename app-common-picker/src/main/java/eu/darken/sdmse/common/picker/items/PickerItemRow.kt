package eu.darken.sdmse.common.picker.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.areas.label
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.labelRes
import eu.darken.sdmse.common.picker.PickerViewModel

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

    val icon = if (isRoot) Icons.Outlined.Folder else item.lookup.fileType.icon
    val primary = if (isRoot) item.lookup.lookedUp.path else item.lookup.name
    val secondary = item.dataArea.type.label.get(context)
    val tertiaryRes = when (item.lookup.fileType) {
        FileType.DIRECTORY, FileType.FILE, FileType.SYMBOLIC_LINK, FileType.UNKNOWN -> item.lookup.fileType.labelRes
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified,
        )
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
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggleSelect() },
            )
        }
    }
}
