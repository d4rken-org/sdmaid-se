package eu.darken.sdmse.common.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.shizuku.service.internal.ShizukuBaseServiceBinder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val handlerThread: HandlerThread by lazy {
        HandlerThread("shizuku:binder-handler")
    }
    private val handler: Handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    val baseServiceBinder: Flow<ShizukuBaseServiceBinder?> = callbackFlow<ShizukuBinderWrapper?> {
        val sendBinder = {
            val binder = Shizuku.getBinder()
            log(TAG) { "Sending binder: $binder" }
            trySendBlocking(binder?.let { ShizukuBinderWrapper(it) })
        }

        val onReceive = Shizuku.OnBinderReceivedListener {
            log(TAG) { "binderFlow(): OnBinderReceivedListener" }
            sendBinder()
        }
        val onDead = Shizuku.OnBinderDeadListener {
            log(TAG) { "binderFlow(): OnBinderDeadListener :(" }
            sendBinder()
        }
        log(TAG) { "binderFlow(): Registering..." }

        Shizuku.addBinderReceivedListener(onReceive, handler)
        Shizuku.addBinderDeadListener(onDead, handler)

        sendBinder()

        log(TAG) { "binderFlow(): Awaiting close" }
        awaitClose {
            log(TAG) { "binderFlow(): Closing..." }
            Shizuku.removeBinderReceivedListener(onReceive)
            Shizuku.removeBinderDeadListener(onDead)
        }
    }
        .map { binder -> binder?.let { ShizukuBaseServiceBinder(it) } }


    val permissionGrantEvents: Flow<ShizukuPermissionRequest> = callbackFlow {
        val requestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            log(TAG) { "permissionFlow(): Event: $requestCode -> $grantResult" }
            trySendBlocking(ShizukuPermissionRequest(requestCode = requestCode, grantResult = grantResult))
        }

        log(TAG) { "permissionFlow(): Registering..." }
        Shizuku.addRequestPermissionResultListener(requestListener, handler)

        log(TAG) { "permissionFlow(): Awaiting close" }
        awaitClose {
            log(TAG) { "permissionFlow(): Closing..." }
            Shizuku.removeRequestPermissionResultListener(requestListener)
        }
    }
        .setupCommonEventHandlers(TAG) { "grantEvents" }

    data class ShizukuPermissionRequest(
        val requestCode: Int,
        val grantResult: Int,
    )

    suspend fun isGranted(): Boolean? {
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: IllegalStateException) {
            log(TAG, WARN) { "isGranted(): $e" }
            null
        }
        log(TAG) { "isGranted()=$granted" }
        return granted
    }


    suspend fun isCompatible(): Boolean {
        return !Shizuku.isPreV11()
    }

    suspend fun requestPermission() {
        log(TAG) { "requestPermission()" }
        Shizuku.requestPermission(433)
    }

    companion object {
        private val TAG = logTag("Shizuku", "Wrapper")
    }

}