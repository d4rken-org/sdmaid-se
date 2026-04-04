package eu.darken.sdmse.appcontrol.core.restore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.isValidHiltContext

class UnarchiveReceiver : BroadcastReceiver() {

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

        if (!context.isValidHiltContext()) {
            log(TAG, WARN) { "Invalid Hilt context (${context.applicationContext.javaClass}), skipping (backup/restore?)" }
            return
        }

        val entryPoint = try {
            EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to get entry point: $e" }
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

        entryPoint.unarchiveManager().onUnarchiveResult(requestCode, result)
    }

    companion object {
        private val TAG = logTag("AppControl", "UnarchiveReceiver")

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface ReceiverEntryPoint {
            fun unarchiveManager(): UnarchiveManager
        }
    }
}
