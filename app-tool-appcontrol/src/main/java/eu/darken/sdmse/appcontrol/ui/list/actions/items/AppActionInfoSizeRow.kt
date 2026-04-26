package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.text.format.Formatter
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps

@Composable
fun AppActionInfoSizeRow(
    modifier: Modifier = Modifier,
    sizes: PkgOps.SizeStats,
    showAppLine: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.appcontrol_app_sizes_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = Formatter.formatFileSize(context, sizes.total),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (showAppLine) {
                LabeledValue(
                    label = stringResource(R.string.appcontrol_app_sizes_apk_label),
                    value = Formatter.formatFileSize(context, sizes.appBytes),
                )
            }
            LabeledValue(
                label = stringResource(R.string.appcontrol_app_sizes_data_label),
                value = Formatter.formatFileSize(context, sizes.userDataBytes),
            )
            LabeledValue(
                label = stringResource(R.string.appcontrol_app_sizes_cache_label),
                value = Formatter.formatFileSize(context, sizes.cacheBytes),
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
