package eu.darken.sdmse.exclusion.ui.list.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import eu.darken.sdmse.common.compose.SelectableListRow
import eu.darken.sdmse.exclusion.ui.list.ExclusionListViewModel

@Composable
fun PkgExclusionRow(
    modifier: Modifier = Modifier,
    row: ExclusionListViewModel.Row.Pkg,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    SelectableListRow(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (row.pkg != null) {
                AsyncImage(
                    modifier = Modifier.size(32.dp),
                    model = row.pkg,
                    contentDescription = null,
                )
            } else {
                Icon(
                    imageVector = Icons.TwoTone.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = row.exclusion.pkgId.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.isDefault) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
