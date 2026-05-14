package eu.darken.sdmse.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Standard selectable row container for tool list screens.
 *
 * Provides the shared chrome: full-width Row, selection background
 * ([MaterialTheme.colorScheme.secondaryContainer] when [selected]),
 * combined click/long-click handler, and content padding.
 *
 * Colors and shape are intentionally not parameterized — the abstraction's
 * purpose is to enforce visual consistency across tools.
 */
@Composable
fun SelectableListRow(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/**
 * 40dp leading icon container used by tool list rows.
 *
 * Renders a rounded [MaterialTheme.colorScheme.surfaceContainerHigh] box.
 * If [onClick] is provided, the box itself becomes a separate tap target
 * (e.g. for "open details" while the surrounding row toggles selection).
 * Pass `null` for both handlers when the icon should not be independently clickable.
 *
 * [onLongClick] is only honored when [onClick] is also non-null — this mirrors
 * `Modifier.combinedClickable`'s own contract. Providing a long-click without a
 * click would be silently dropped, so the function fails fast instead.
 */
@Composable
fun SelectableListRowIconBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    require(onClick != null || onLongClick == null) {
        "SelectableListRowIconBox: onLongClick supplied without onClick — long-click would be silently dropped."
    }
    val clickModifier = if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
            )
            .then(clickModifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Preview2
@Composable
private fun SelectableListRowPreview() {
    PreviewWrapper {
        SelectableListRow(
            selected = false,
            onClick = {},
            onLongClick = {},
        ) {
            SelectableListRowIconBox {
                Icon(
                    imageVector = Icons.TwoTone.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Primary text", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Secondary text",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SelectableListRowSelectedPreview() {
    PreviewWrapper {
        SelectableListRow(
            selected = true,
            onClick = {},
            onLongClick = {},
        ) {
            SelectableListRowIconBox(onClick = {}, onLongClick = {}) {
                Icon(
                    imageVector = Icons.TwoTone.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Primary text", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Secondary text",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
