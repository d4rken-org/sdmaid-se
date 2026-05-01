package eu.darken.sdmse.common.debug.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR

@Composable
fun RecorderConsentDialog(
    onStartRecording: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.support_debuglog_label)) },
        text = { Text(stringResource(R.string.settings_debuglog_explanation)) },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onOpenPrivacyPolicy) {
                    Text(stringResource(R.string.settings_privacy_policy_label))
                }
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(CommonR.string.general_cancel_action))
                    }
                    TextButton(
                        onClick = {
                            onStartRecording()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.debug_debuglog_record_action))
                    }
                }
            }
        },
    )
}
