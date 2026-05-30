package eu.darken.sdmse.common.debug.recorder.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.R as CommonR

@Composable
fun RecorderConsentDialog(
    onStartRecording: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDismiss: () -> Unit,
) {
    SdmConfirmDialog(
        title = stringResource(R.string.support_debuglog_label),
        message = stringResource(R.string.settings_debuglog_explanation),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(R.string.debug_debuglog_record_action),
            onClick = {
                onStartRecording()
                onDismiss()
            },
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            onClick = onDismiss,
        ),
        neutral = SdmDialogAction(
            label = stringResource(R.string.settings_privacy_policy_label),
            // Dismiss too: SdmConfirmDialog doesn't auto-dismiss on neutral (the legacy
            // MaterialAlertDialog did), so the dialog would otherwise stay open behind the browser.
            onClick = {
                onOpenPrivacyPolicy()
                onDismiss()
            },
        ),
    )
}

@Preview2
@Composable
private fun RecorderConsentDialogPreview() {
    PreviewWrapper {
        RecorderConsentDialog(
            onStartRecording = {},
            onOpenPrivacyPolicy = {},
            onDismiss = {},
        )
    }
}
