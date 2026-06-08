package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun DebugToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    highlight: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlight) MaterialTheme.colorScheme.error else LocalContentColor.current,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Row owns the toggle (via toggleable); the switch is presentational to avoid double events.
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Preview2
@Composable
private fun DebugToggleRowPreview() {
    PreviewWrapper {
        var checked by remember { mutableStateOf(true) }
        DebugToggleRow(
            text = "Trace logging",
            description = "Verbose step-by-step logs. Slows the app down noticeably.",
            checked = checked,
            onCheckedChange = { checked = it },
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Preview2
@Composable
private fun DebugToggleRowHighlightPreview() {
    PreviewWrapper {
        var checked by remember { mutableStateOf(true) }
        DebugToggleRow(
            text = "Dry-run mode",
            description = "Simulate cleanup — files are scanned but nothing is deleted.",
            checked = checked,
            onCheckedChange = { checked = it },
            highlight = checked,
            modifier = Modifier.padding(8.dp),
        )
    }
}
