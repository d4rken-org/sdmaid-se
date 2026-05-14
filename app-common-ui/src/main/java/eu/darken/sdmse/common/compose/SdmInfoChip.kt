package eu.darken.sdmse.common.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Static, non-clickable label chip with a leading icon.
 *
 * Use for visual tagging (e.g. system-app marker, match type). The icon is decorative
 * by default — pass [iconContentDescription] if the icon carries meaning beyond [label].
 *
 * For interactive filter chips, use Material3 `FilterChip` / `InputChip` instead.
 */
@Composable
fun SdmInfoChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    iconSize: Dp = 14.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    iconContentDescription: String? = null,
) = SdmInfoChipInternal(
    modifier = modifier,
    label = label,
    containerColor = containerColor,
    contentColor = contentColor,
    textStyle = textStyle,
    contentPadding = contentPadding,
    iconSlot = {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    },
)

/**
 * Painter variant of [SdmInfoChip] for drawable-resource icons.
 */
@Composable
fun SdmInfoChip(
    modifier: Modifier = Modifier,
    icon: Painter,
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    iconSize: Dp = 14.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    iconContentDescription: String? = null,
) = SdmInfoChipInternal(
    modifier = modifier,
    label = label,
    containerColor = containerColor,
    contentColor = contentColor,
    textStyle = textStyle,
    contentPadding = contentPadding,
    iconSlot = {
        Icon(
            painter = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    },
)

@Composable
private fun SdmInfoChipInternal(
    modifier: Modifier,
    label: String,
    containerColor: Color,
    contentColor: Color,
    textStyle: TextStyle,
    contentPadding: PaddingValues,
    iconSlot: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconSlot()
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = textStyle,
                color = contentColor,
            )
        }
    }
}

@Preview2
@Composable
private fun SdmInfoChipPreview() {
    PreviewWrapper {
        SdmInfoChip(
            icon = SdmIcons.CodeEqualBox,
            label = "Match: hash",
        )
    }
}
