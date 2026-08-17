package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.coroutine.runDetachedWithTimeout
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * Package that declares the Shizuku permission, or null if no installed app declares it.
     *
     * Detects Shizuku via its permission ([ShizukuProvider.PERMISSION]) instead of a fixed package
     * name. The permission name is shared across Shizuku forks, so this keeps working when a fork
     * hides its package from enumeration ("Hide Shizuku from other apps") or ships under a different
     * package name. Permissions live in a global namespace, so the lookup isn't subject to the
     * package-visibility filtering that hides the app itself.
     */
    suspend fun getManagerPackage(): String? = withContext(dispatcherProvider.IO) {
        try {
            context.packageManager
                .getPermissionInfo(ShizukuProvider.PERMISSION, 0)
                .packageName
                ?.takeUnless { it.isBlank() }
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG) { "getManagerPackage(): Shizuku permission not declared by any app" }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "getManagerPackage(): Lookup failed: ${e.asLog()}" }
            null
        }
    }

    private val handlerThread: HandlerThread by lazy {
        HandlerThread("shizuku:binder-handler")
    }
    private val handler: Handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    val baseServiceBinder: Flow<ShizukuBaseServiceBinder?> = callbackFlow {
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

    // Seams for the two Shizuku statics behind isGranted(). Mirrors the AdbHostLauncher seam: the
    // Shizuku statics are untouchable in JVM unit tests, even via mockkStatic, because the class
    // initializer builds a Handler on the main Looper. Overridden in tests, never in production.
    internal var pingBinderAction: () -> Boolean = { Shizuku.pingBinder() }
    internal var checkSelfPermissionAction: () -> Int = { Shizuku.checkSelfPermission() }

    /** Overridden in tests to keep the wedge case fast, never in production. */
    internal var ipcTimeoutMs: Long = IPC_TIMEOUT_MS

    private fun pingBinderSafe(): Boolean = try {
        pingBinderAction()
    } catch (e: NullPointerException) {
        // Upstream race: the binder can be nulled between Shizuku's null check and the ping.
        false
    }

    suspend fun isGranted(): Boolean? {
        // Both statics below are synchronous binder transactions that can wedge against a Shizuku
        // server that is alive but not servicing requests: pingBinder() is a PING_TRANSACTION, and
        // checkSelfPermission() does a real round-trip whenever its granted state was not latched yet
        // (first connection). Neither is covered by AdbHostLauncher's connect watchdog, because both
        // run before it is armed, so an unbounded wedge here reproduces the eternal setup spinner that
        // watchdog exists to prevent. Detached + bounded: a wedged binder thread leaks, we don't hang.
        // GrantState (not Boolean?) so the helper's `null` stays reserved for "timed out".
        val state = appScope.runDetachedWithTimeout(dispatcherProvider.IO, ipcTimeoutMs) {
            // Shizuku.checkSelfPermission() latches its granted state process-wide and never clears it
            // on binder death, so without this gate it reports a stale `true` with no live binder
            // behind it. UNKNOWN already means "cannot know" (see the ISE catch below), which covers
            // this case too.
            if (!pingBinderSafe()) {
                log(TAG) { "isGranted()=null (binder not alive)" }
                return@runDetachedWithTimeout GrantState.UNKNOWN
            }
            val granted = try {
                checkSelfPermissionAction() == PackageManager.PERMISSION_GRANTED
            } catch (e: IllegalStateException) {
                log(TAG, WARN) { "isGranted(): $e" }
                log(TAG) { "isGranted()=null" }
                return@runDetachedWithTimeout GrantState.UNKNOWN
            }
            log(TAG) { "isGranted()=$granted" }
            if (granted) GrantState.GRANTED else GrantState.DENIED
        }
        if (state == null) {
            log(TAG, WARN) { "isGranted()=null (Shizuku did not respond within ${ipcTimeoutMs}ms)" }
        }
        return when (state) {
            GrantState.GRANTED -> true
            GrantState.DENIED -> false
            GrantState.UNKNOWN, null -> null
        }
    }

    private enum class GrantState { GRANTED, DENIED, UNKNOWN }

    suspend fun isCompatible(): Boolean {
        return !Shizuku.isPreV11()
    }

    suspend fun requestPermission() = withContext(dispatcherProvider.IO) {
        log(TAG) { "requestPermission()" }
        Shizuku.requestPermission(433)
    }

    companion object {
        private val TAG = logTag("ADB", "Shizuku", "Wrapper")

        /**
         * Combined budget for the two binder round-trips behind [isGranted].
         *
         * Deliberately generous rather than tight: the job here is only to turn "never returns" into
         * "eventually gives up". A too-tight bound would report a slow-but-working Shizuku as
         * unavailable, and low-end devices under memory pressure (the exact hardware this defect shows
         * up on) are where both a real wedge and a slow answer are most likely.
         */
        internal const val IPC_TIMEOUT_MS = 15 * 1000L
    }

}