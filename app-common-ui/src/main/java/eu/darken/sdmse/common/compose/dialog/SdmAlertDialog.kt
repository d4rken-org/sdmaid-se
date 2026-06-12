package eu.darken.sdmse.common.compose.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import eu.darken.sdmse.common.compose.focusHighlightRing
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Drop-in replacement for Material3's [AlertDialog] that restores the D-pad/keyboard focus ring
 * inside the dialog. Dialogs render in their own window, outside the FocusHighlightOverlay that
 * SdmSeTheme mounts around the main content — without this, dialog buttons fall back to M3's
 * near-invisible focus state layer on TV. The ring modifier rides on the dialog's content box
 * (M3 applies the modifier inside the dialog window), so no dialog layout is reimplemented.
 */
@Composable
fun SdmAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        // A confirm key still held from BEFORE this dialog opened (e.g. the D-pad long press that
        // triggered it) auto-repeats into the newly focused dialog window. Compose's clickable
        // registers a repeat key-down as a fresh press, so releasing the key would click the
        // initially focused button and instantly dismiss the dialog. Framework Views ignore
        // repeat downs (repeatCount > 0) for click handling; mirror that for the whole dialog.
        modifier = modifier
            .onPreviewKeyEvent { it.isRepeatedConfirmKeyDown }
            .focusHighlightRing(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

private val KeyEvent.isRepeatedConfirmKeyDown: Boolean
    get() = type == KeyEventType.KeyDown &&
        nativeKeyEvent.repeatCount > 0 &&
        when (key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
            else -> false
        }

@Preview2
@Composable
private fun SdmAlertDialogPreview() {
    PreviewWrapper {
        SdmAlertDialog(
            onDismissRequest = {},
            title = { Text("Delete everything?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {}) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {}) { Text("Cancel") }
            },
        )
    }
}
