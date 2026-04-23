package eu.darken.sdmse.common.compose.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    enabled: Boolean = true,
) {
    SettingsBaseItem(
        icon = icon,
        iconPainter = iconPainter,
        title = title,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        subtitle = subtitle,
        enabled = enabled,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.padding(start = 16.dp),
            )
        },
    )
}

@Preview2
@Composable
private fun SettingsSwitchItemPreview() {
    PreviewWrapper {
        SettingsSwitchItem(
            icon = Icons.TwoTone.Settings,
            title = "Settings",
            subtitle = "General settings",
            checked = true,
            onCheckedChange = {},
        )
    }
}
