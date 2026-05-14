package eu.darken.sdmse.common.debug.recorder.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction

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
            onClick = onOpenPrivacyPolicy,
        ),
    )
}
