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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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

    @OptIn(DelicateCoroutinesApi::class) // isClosedForSend, to skip the close() on intentional teardown
    fun <Service : IInterface, Host : AdbConnection> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        options: AdbHostOptions,
    ): Flow<ConnectionWrapper<Service, Host>> = callbackFlow {
        if (serviceFactory.apiVersion() < 10) throw IllegalStateException("Shizuku API10+ required")

        // Completed only once a connection was actually handed downstream, this is what the
        // connect-watchdog below waits for.
        val ready = CompletableDeferred<Unit>()

        val service = serviceFactory.create(
            hostClass = hostClass,
            options = options,
            onConnected = fun(binder: IBinder?) {
                log(TAG) { "onServiceConnected(binder=$binder)" }
                // Hop off Shizuku's callback thread (main): the handshake below does binder
                // transactions, a wedged one would ANR and a DeadObjectException would crash the app
                // uncaught. The producer scope runs on IO (see AdbServiceClient.parentScope).
                this@callbackFlow.launch {
                    try {
                        log(TAG) { "Handshaking with the user service, options=$options" }
                        val (userConnection, baseConnection) = serviceFactory.handshake<Service, Host>(
                            binder = binder,
                            serviceClass = serviceClass,
                            options = options,
                        )
                        log(TAG) { "onServiceConnected(...) -> $userConnection" }
                        send(ConnectionWrapper(userConnection, baseConnection))
                        ready.complete(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, WARN) { "User service handshake failed: ${e.asLog()}" }
                        close(AdbException("Shizuku user service handshake failed", e))
                    }
                }
            },
            onDisconnected = {
                // Fires on an UNEXPECTED disconnect (Shizuku/host died outside our own unbind). Close the
                // flow so the SharedResource generation tears down and the next get() re-binds, instead of
                // handing out a dead connection during the keep-alive window. On intentional teardown the
                // channel is already closed (isClosedForSend), so this is a no-op.
                if (!isClosedForSend) {
                    log(TAG, WARN) { "Shizuku user service disconnected unexpectedly, closing connection" }
                    close(AdbException("Shizuku user service disconnected"))
                }
            },
        )

        // Started BEFORE bind(): bindUserService() is itself a synchronous binder transaction that can
        // wedge, and we can't interrupt that binder thread — but the watchdog still releases everyone
        // waiting on this flow instead of leaving them hanging forever.
        launch {
            if (withTimeoutOrNull(CONNECT_TIMEOUT_MS) { ready.await() } == null) {
                log(TAG, WARN) { "User service did not connect within ${CONNECT_TIMEOUT_MS}ms, closing" }
                // Residual epsilon race: a send() completing concurrently with the deadline can tear
                // down a connection that just came up. CompletableDeferred + withTimeoutOrNull narrows
                // that window but can't close it; the next acquire re-binds.
                close(AdbException("Shizuku user service did not connect within ${CONNECT_TIMEOUT_MS}ms"))
            }
        }

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

        // How long to wait for onServiceConnected after binding. Generous: a cold AdbHost start is a
        // multi-second affair (see AdbServiceClient's keep-alive rationale). AppOpsNext uses 12s for
        // the same probe against the same upstream Shizuku defect where bindUserService() returns but
        // the connection callback never fires (MediaTek/HyperOS, Shizuku 13.6.0).
        private const val CONNECT_TIMEOUT_MS = 15 * 1000L
    }
}
