package eu.darken.sdmse.appcontrol.core.restore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@AndroidEntryPoint
class UnarchiveReceiver : BroadcastReceiver() {

    @Inject lateinit var unarchiveManager: UnarchiveManager

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }

        if (intent.action != UnarchiveManager.ACTION_UNARCHIVE_RESULT) {
            log(TAG) { "Ignoring unknown action: ${intent.action}" }
            return
        }

        val requestCode = intent.getIntExtra(UnarchiveManager.EXTRA_REQUEST_CODE, -1)
        if (requestCode == -1) {
            log(TAG) { "Missing request code in intent" }
            return
        }

        val status = intent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_STATUS, -1)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""

        log(TAG) { "Unarchive result: pkg=$packageName, status=$status, message=$statusMessage" }

        val result = UnarchiveManager.UnarchiveResult(
            packageName = packageName,
            status = status,
            statusMessage = statusMessage,
        )

        unarchiveManager.onUnarchiveResult(requestCode, result)
    }

    companion object {
        private val TAG = logTag("AppControl", "UnarchiveReceiver")
    }
}
