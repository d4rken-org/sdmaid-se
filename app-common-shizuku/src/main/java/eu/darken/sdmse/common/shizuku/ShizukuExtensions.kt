package eu.darken.sdmse.common.shizuku

import android.os.Handler
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.ShizukuBinderWrapper


fun createShizukuBinderFlow(handler: Handler): Flow<ShizukuBinderWrapper?> = callbackFlow {
    val sendBinder = {
        val binder = Shizuku.getBinder()
        log(TAG) { "Sending binder: $binder" }
        binder?.let { trySendBlocking(ShizukuBinderWrapper(it)) }
    }

    val onReceive = OnBinderReceivedListener {
        log(TAG) { "binderFlow(): OnBinderReceivedListener" }
        sendBinder()
    }
    val onDead = Shizuku.OnBinderDeadListener {
        log(TAG) { "binderFlow(): OnBinderDeadListener :(" }
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

data class ShizukuPermissionRequest(
    val requestCode: Int,
    val grantResult: Int,
)

fun createShizukuPermissionFlow(handler: Handler): Flow<ShizukuPermissionRequest> = callbackFlow {
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

private val TAG = logTag("Shizuku", "Extensions")
