package eu.darken.sdmse.common.debug.recorder.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R

class ShortRecordingDialog(
    private val context: Context,
    private val onContinue: () -> Unit,
    private val onStopAnyway: () -> Unit,
) {
    fun show() {
        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.debug_debuglog_short_recording_title)
            setMessage(R.string.debug_debuglog_short_recording_desc)
            setPositiveButton(R.string.debug_debuglog_short_recording_continue_action) { _, _ -> onContinue() }
            setNegativeButton(R.string.debug_debuglog_short_recording_stop_action) { _, _ -> onStopAnyway() }
            setOnCancelListener { onContinue() }
        }.show()
    }
}
