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
        modifier = modifier.focusHighlightRing(),
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
