package eu.darken.sdmse.common.adb.service.internal

import android.os.IBinder
import android.os.IInterface
import dagger.Reusable
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * The Shizuku touchpoints (version check, bind/unbind, ServiceConnection) are behind an injectable
 * seam ([ShizukuUserServiceFactory]) so this orchestration — especially the finally-block teardown —
 * is unit-testable. See AdbHostLauncherSeam.kt.
 */
@Reusable
class AdbHostLauncher @Inject constructor(
    private val serviceFactory: ShizukuUserServiceFactory,
) {

    fun <Service : IInterface, Host : AdbConnection> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        options: AdbHostOptions,
    ): Flow<ConnectionWrapper<Service, Host>> = callbackFlow {
        if (serviceFactory.apiVersion() < 10) throw IllegalStateException("Shizuku API10+ required")

        val service = serviceFactory.create(
            hostClass = hostClass,
            options = options,
            onConnected = fun(binder: IBinder?) {
                log(TAG) { "onServiceConnected(binder=$binder)" }

                if (binder?.pingBinder() != true) {
                    log(TAG) { "onServiceConnected(...) Invalid binder (ping failed)" }
                    return
                }

                val baseConnection = try {
                    AdbConnection.Stub.asInterface(binder)!!
                } catch (e: Exception) {
                    close(AdbException("Failed to get base connection", e))
                    return
                }

                // Initial options, Shizuku has no init arguments through which these can be supplied earlier
                log(TAG) { "Updating host options to $options" }
                baseConnection.updateHostOptions(options)

                val userConnection = try {
                    baseConnection.userConnection.getInterface(serviceClass) as Service
                } catch (e: Exception) {
                    close(AdbException("Failed to get user connection (ADB)", e))
                    return
                }

                log(TAG) { "onServiceConnected(...) -> $userConnection" }
                @Suppress("UNCHECKED_CAST")
                trySendBlocking(ConnectionWrapper(userConnection, baseConnection as Host))
            },
        )

        var bound = false
        try {
            service.bind()
            bound = true

            log(TAG) { "Waiting for flow to close" }
            awaitClose { log(TAG) { "awaitClose() reached, flow is closing…" } }
        } finally {
            // Runs on cancellation too. Mirrors RootHostLauncher: cleanup lives in the finally (not
            // only in awaitClose) so a throw between bind and awaitClose can't leak the Shizuku
            // binding, and unbind is best-effort so a DeadObjectException can't mask the cancellation.
            if (bound) {
                log(TAG) { "Unbinding Shizuku user service…" }
                runCatching { service.unbind() }
                    .onFailure { log(TAG, WARN) { "unbindUserService() failed: ${it.asLog()}" } }
                // Bounded wait for the actual disconnect; without it, quick flow restarts can cause
                // DeadObjectExceptions from our Shizuku service binder.
                withContext(NonCancellable) {
                    withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { service.awaitDisconnect() }
                }
                log(TAG) { "Shizuku user service unbound." }
            }
        }
    }

    data class ConnectionWrapper<Service : IInterface, Host : AdbConnection>(
        val service: Service,
        val host: Host,
    )

    companion object {
        private val TAG = logTag("ADB", "Host", "Launcher")

        // How long to wait for the Shizuku service to actually disconnect after unbinding before
        // giving up — bounded so teardown can't hang.
        private const val DISCONNECT_TIMEOUT_MS = 500L
    }
}
