package eu.darken.sdmse.common.debug.recorder.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R

@Composable
fun ShortRecordingDialog(
    onContinue: () -> Unit,
    onStopAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onContinue()
            onDismiss()
        },
        title = { Text(stringResource(R.string.debug_debuglog_short_recording_title)) },
        text = { Text(stringResource(R.string.debug_debuglog_short_recording_desc)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onContinue()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.debug_debuglog_short_recording_continue_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onStopAnyway()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.debug_debuglog_short_recording_stop_action))
            }
        },
    )
}
