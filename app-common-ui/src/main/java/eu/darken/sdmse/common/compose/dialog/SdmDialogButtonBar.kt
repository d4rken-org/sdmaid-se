package eu.darken.sdmse.common.compose.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

private val HorizontalSpacing = 4.dp
private val VerticalSpacing = 2.dp

/**
 * Adaptive dialog action bar that never wraps label text inside buttons.
 *
 * When all buttons fit side by side at their natural single-line width, the classic horizontal
 * layout is used: neutral on the leading edge, negative+positive on the trailing edge.
 * Otherwise the bar switches to a vertical, end-aligned stack: positive on top, then negative,
 * then neutral (Material stacked-button convention).
 *
 * Initial D-pad/keyboard focus is deterministic: an action flagged [SdmDialogAction.initialFocus]
 * wins (first in positive/negative/neutral order), otherwise the negative (cancel) when present,
 * else the positive. Without this the framework picks spatially, i.e. whatever button happens to
 * sit leftmost — for destructive confirms the safe default must be the cancel action.
 */
@Composable
fun SdmDialogButtonBar(
    modifier: Modifier = Modifier,
    positive: SdmDialogAction,
    negative: SdmDialogAction? = null,
    neutral: SdmDialogAction? = null,
) {
    val focusTarget = listOfNotNull(positive, negative, neutral).firstOrNull { it.initialFocus }
        ?: negative
        ?: positive
    val focusRequester = remember { FocusRequester() }
    // Keyed on the input mode, NOT the actions: action instances are recreated on recomposition
    // and would re-yank focus from wherever the user moved it. In touch mode the request fails
    // silently (buttons aren't touch-focusable); the first remote/keyboard key flips the mode
    // and re-runs this to claim focus properly.
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(inputModeManager.inputMode) {
        runCatching { focusRequester.requestFocus() }
    }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // Composition order positive, negative, neutral matches M3's traversal convention
            // (confirm action first) and the visual top-to-bottom order in vertical mode.
            ActionButton(positive, if (positive === focusTarget) focusRequester else null)
            if (negative != null) ActionButton(negative, if (negative === focusTarget) focusRequester else null)
            if (neutral != null) ActionButton(neutral, if (neutral === focusTarget) focusRequester else null)
        },
    ) { measurables, constraints ->
        val gapH = HorizontalSpacing.roundToPx()
        val gapV = VerticalSpacing.roundToPx()

        // Fit decision uses true single-line widths; capped measurement could under-report.
        val intrinsicTotal = measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } +
            (measurables.size - 1) * gapH
        val fitsHorizontally = !constraints.hasBoundedWidth || intrinsicTotal <= constraints.maxWidth

        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val positivePlaceable = placeables[0]
        val negativePlaceable = if (negative != null) placeables[1] else null
        val neutralPlaceable = if (neutral != null) placeables[1 + (if (negative != null) 1 else 0)] else null

        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeables.sumOf { it.width } + (placeables.size - 1) * gapH
        }

        if (fitsHorizontally) {
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                neutralPlaceable?.placeRelative(0, (height - neutralPlaceable.height) / 2)
                var x = width - positivePlaceable.width
                positivePlaceable.placeRelative(x, (height - positivePlaceable.height) / 2)
                negativePlaceable?.let {
                    x -= gapH + it.width
                    it.placeRelative(x, (height - it.height) / 2)
                }
            }
        } else {
            val stacked = listOfNotNull(positivePlaceable, negativePlaceable, neutralPlaceable)
            val height = stacked.sumOf { it.height } + (stacked.size - 1) * gapV
            layout(width, height) {
                var y = 0
                stacked.forEach {
                    it.placeRelative(width - it.width, y)
                    y += it.height + gapV
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: SdmDialogAction, focusRequester: FocusRequester? = null) {
    TextButton(
        modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
        onClick = action.onClick,
        enabled = action.enabled,
    ) {
        Text(
            text = action.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview2
@Composable
private fun SdmDialogButtonBarHorizontalPreview() {
    PreviewWrapper {
        SdmDialogButtonBar(
            positive = SdmDialogAction(label = "Delete", onClick = {}),
            negative = SdmDialogAction(label = "Cancel", onClick = {}),
            neutral = SdmDialogAction(label = "Show details", onClick = {}),
        )
    }
}

@Preview2
@Composable
private fun SdmDialogButtonBarVerticalPreview() {
    PreviewWrapper {
        Box(modifier = Modifier.width(220.dp)) {
            SdmDialogButtonBar(
                positive = SdmDialogAction(label = "Delete everything", onClick = {}),
                negative = SdmDialogAction(label = "Cancel the action", onClick = {}),
                neutral = SdmDialogAction(label = "Show more details", onClick = {}),
            )
        }
    }
}

@Preview2
@Composable
private fun SdmDialogButtonBarPositiveOnlyPreview() {
    PreviewWrapper {
        SdmDialogButtonBar(
            positive = SdmDialogAction(label = "OK", onClick = {}),
        )
    }
}
