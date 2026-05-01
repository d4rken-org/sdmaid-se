package eu.darken.sdmse.common.compose.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun SettingsPreferenceItem(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconTint: Color? = null,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    requiresUpgrade: Boolean = false,
    onUpgrade: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.5f

    SettingsBaseItem(
        icon = icon,
        iconPainter = iconPainter,
        iconTint = iconTint,
        title = title,
        onClick = if (requiresUpgrade) onUpgrade else onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        subtitle = subtitle,
        enabled = enabled,
        requiresUpgrade = requiresUpgrade,
        trailingContent = if (value != null) {
            {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha),
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        } else null,
    )
}

@Preview2
@Composable
private fun SettingsPreferenceItemPreview() {
    PreviewWrapper {
        SettingsPreferenceItem(
            icon = Icons.TwoTone.Settings,
            title = "Settings",
            subtitle = "General settings",
            onClick = {},
            value = "Value",
        )
    }
}

@Preview2
@Composable
private fun SettingsPreferenceItemGatedPreview() {
    PreviewWrapper {
        SettingsPreferenceItem(
            icon = Icons.TwoTone.Settings,
            title = "Theme style",
            subtitle = "Pick the app color scheme",
            value = "Default",
            onClick = {},
            requiresUpgrade = true,
            onUpgrade = {},
        )
    }
}
