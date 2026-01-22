package eu.darken.sdmse.appcontrol.core.restore

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnarchiveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pkgOps: PkgOps,
) {
    private val pendingCallbacks = mutableMapOf<Int, Channel<UnarchiveResult>>()
    private val requestCodeCounter = AtomicInteger(0)

    data class UnarchiveResult(
        val packageName: String,
        val status: Int,
        val statusMessage: String?,
    ) {
        val isSuccess: Boolean
            get() = status == PackageInstaller.STATUS_SUCCESS
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    suspend fun requestUnarchive(pkgName: String, timeoutMs: Long = 5000L): UnarchiveResult {
        log(TAG, VERBOSE) { "requestUnarchive($pkgName)" }

        val requestCode = requestCodeCounter.incrementAndGet()
        val resultChannel = Channel<UnarchiveResult>(1)

        synchronized(pendingCallbacks) {
            pendingCallbacks[requestCode] = resultChannel
        }

        try {
            val intent = Intent(context, UnarchiveReceiver::class.java).apply {
                action = ACTION_UNARCHIVE_RESULT
                putExtra(EXTRA_REQUEST_CODE, requestCode)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            log(TAG) { "Calling pkgOps.requestUnarchive for $pkgName via Root/ADB" }
            pkgOps.requestUnarchive(pkgName, pendingIntent.intentSender)

            return withTimeout(timeoutMs) {
                resultChannel.receive()
            }
        } finally {
            synchronized(pendingCallbacks) {
                pendingCallbacks.remove(requestCode)
            }
        }
    }

    internal fun onUnarchiveResult(requestCode: Int, result: UnarchiveResult) {
        log(TAG, VERBOSE) { "onUnarchiveResult($requestCode, $result)" }

        val channel = synchronized(pendingCallbacks) {
            pendingCallbacks[requestCode]
        }

        if (channel == null) {
            log(TAG, WARN) { "No pending callback for requestCode=$requestCode" }
            return
        }

        val sent = channel.trySend(result)
        if (!sent.isSuccess) {
            log(TAG, ERROR) { "Failed to send result to channel: ${sent.exceptionOrNull()}" }
        }
    }

    companion object {
        internal const val ACTION_UNARCHIVE_RESULT = "eu.darken.sdmse.action.UNARCHIVE_RESULT"
        internal const val EXTRA_REQUEST_CODE = "eu.darken.sdmse.extra.REQUEST_CODE"

        private val TAG = logTag("AppControl", "UnarchiveManager")
    }
}
