package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun DebugToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) MaterialTheme.colorScheme.error else LocalContentColor.current,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview2
@Composable
private fun DebugToggleRowPreview() {
    PreviewWrapper {
        var checked by remember { mutableStateOf(true) }
        DebugToggleRow(
            text = "Enable verbose debug logging",
            checked = checked,
            onCheckedChange = { checked = it },
        )
    }
}

@Preview2
@Composable
private fun DebugToggleRowHighlightPreview() {
    PreviewWrapper {
        var checked by remember { mutableStateOf(false) }
        DebugToggleRow(
            text = "Crash reporter enabled",
            checked = checked,
            onCheckedChange = { checked = it },
            highlight = true,
        )
    }
}
