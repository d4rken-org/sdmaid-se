package eu.darken.sdmse.appcontrol.ui.list.actions.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun AppActionRow(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    description: String?,
    onClick: () -> Unit,
) {
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
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AppActionRowPreview() {
    PreviewWrapper {
        AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.PowerSettingsNew),
            title = "Force stop",
            description = "Stop the app and all its background services",
            onClick = {},
        )
    }
}
