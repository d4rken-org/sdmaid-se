package eu.darken.sdmse.common.compose.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

enum class SdmTooltipAnchor { ABOVE, BELOW }

/**
 * An [IconButton] that shows [label] as a long-press tooltip (and as the icon's content description),
 * restoring the menu-item "cheat sheet" affordance the View toolbar gave for free.
 *
 * Use this for every toolbar/menu icon affordance (top app bars, bottom bars, overflow triggers).
 */
@Composable
fun SdmTooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    anchor: SdmTooltipAnchor = SdmTooltipAnchor.BELOW,
    tint: Color = LocalContentColor.current,
) {
    SdmTooltipIconButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        anchor = anchor,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
        )
    }
}

/**
 * Slot variant for tinted/dynamic/custom content. The caller's [content] renders the icon and is
 * responsible for its own content description.
 *
 * [modifier] is applied to the inner [IconButton] (not the tooltip wrapper) so call-site modifiers
 * such as guided-tour targets keep pointing at the actual button bounds.
 */
@Composable
fun SdmTooltipIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    anchor: SdmTooltipAnchor = SdmTooltipAnchor.BELOW,
    content: @Composable () -> Unit,
) {
    val positioning = when (anchor) {
        SdmTooltipAnchor.BELOW -> TooltipAnchorPosition.Below
        SdmTooltipAnchor.ABOVE -> TooltipAnchorPosition.Above
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}

@Preview2
@Composable
private fun SdmTooltipIconButtonPreview() {
    PreviewWrapper {
        SdmTooltipIconButton(
            icon = Icons.TwoTone.Delete,
            label = "Delete",
            onClick = {},
        )
    }
}
