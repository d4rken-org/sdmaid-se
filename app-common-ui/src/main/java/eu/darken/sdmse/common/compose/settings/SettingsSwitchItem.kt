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
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    enabled: Boolean = true,
    requiresUpgrade: Boolean = false,
    onUpgrade: () -> Unit = {},
) {
    SettingsBaseItem(
        icon = icon,
        iconPainter = iconPainter,
        title = title,
        onClick = if (requiresUpgrade) onUpgrade else { { onCheckedChange(!checked) } },
        modifier = modifier,
        subtitle = subtitle,
        enabled = enabled,
        requiresUpgrade = requiresUpgrade,
        trailingContent = {
            Switch(
                checked = checked,
                // MUST stay null when requiresUpgrade=true — non-Pro taps must never reach
                // onCheckedChange. Paired with enabled=false so the Switch doesn't consume
                // pointer input before it reaches the row's combinedClickable.
                onCheckedChange = if (requiresUpgrade) null else onCheckedChange,
                enabled = enabled && !requiresUpgrade,
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

@Preview2
@Composable
private fun SettingsSwitchItemGatedPreview() {
    PreviewWrapper {
        SettingsSwitchItem(
            icon = Icons.TwoTone.Settings,
            title = "Pro feature",
            subtitle = "Tapping opens the upgrade screen",
            checked = false,
            onCheckedChange = {},
            requiresUpgrade = true,
            onUpgrade = {},
        )
    }
}
