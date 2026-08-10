package eu.darken.sdmse.common.compose.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Lets D-pad UP/DOWN move focus out of a single-line text field.
 *
 * A focused text field consumes the vertical keys for caret movement, so on a device with no TAB
 * key — i.e. every TV remote — a field that holds focus is a trap: the user can type and can back
 * out, but can never reach the dialog's buttons. Handled as a *preview* event so it runs before
 * the field's own key handling, which would otherwise swallow the key first.
 *
 * ONLY for `singleLine` fields, where vertical keys have no legitimate caret use. On a multi-line
 * field this would steal line-to-line navigation.
 *
 * Consumes the key only when focus actually moved, matching the convention used elsewhere for
 * D-pad bridging: with nothing to move to, the key falls through to the default handling rather
 * than being silently eaten.
 */
@Composable
fun Modifier.dpadVerticalFieldEscape(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> return@onPreviewKeyEvent false
        }
        focusManager.moveFocus(direction)
    }
}
