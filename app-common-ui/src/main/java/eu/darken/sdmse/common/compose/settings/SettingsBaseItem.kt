package eu.darken.sdmse.common.compose.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun SettingsBaseItem(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconTinted: Boolean = true,
    iconTint: Color? = null,
    iconSize: Dp = 24.dp,
    subtitle: String? = null,
    enabled: Boolean = true,
    requiresUpgrade: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentAlpha = if (enabled) 1f else 0.5f
        val hasIcon = icon != null || iconPainter != null
        val tint = when {
            iconTint != null -> iconTint.copy(alpha = iconTint.alpha * contentAlpha)
            iconTinted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha)
            else -> Color.Unspecified
        }

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        } else if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (hasIcon) 16.dp else 0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    // Only constrain the title when a badge needs to sit beside it; ungated
                    // rows keep their original wrap behavior.
                    maxLines = if (requiresUpgrade) 1 else Int.MAX_VALUE,
                    overflow = if (requiresUpgrade) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (requiresUpgrade) Modifier.weight(1f, fill = false) else Modifier,
                )
                if (requiresUpgrade) {
                    Spacer(Modifier.width(6.dp))
                    UpgradeBadge()
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f * contentAlpha),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        trailingContent?.invoke()
    }
}

@Preview2
@Composable
private fun SettingsBaseItemPreview() {
    PreviewWrapper {
        Column {
            SettingsBaseItem(
                title = "Simple Item",
                subtitle = "This is a simple item without icon",
                onClick = {},
            )
            SettingsBaseItem(
                title = "Base Item",
                subtitle = "This is a base settings item",
                onClick = {},
                icon = Icons.TwoTone.Settings,
                trailingContent = {
                    Text(
                        text = "Trailing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                },
            )
            SettingsBaseItem(
                title = "Pro-gated item",
                subtitle = "Shows upgrade badge next to the title",
                onClick = {},
                icon = Icons.TwoTone.Settings,
                requiresUpgrade = true,
            )
        }
    }
}
