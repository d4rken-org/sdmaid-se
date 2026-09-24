package eu.darken.sdmse.common.adb.service.internal

import android.os.IBinder
import android.os.IInterface
import dagger.Reusable
import eu.darken.porter.sdk.UserServiceArgs
import eu.darken.sdmse.common.adb.AdbConnectTimeoutException
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import eu.darken.sdmse.common.adb.shizuku.AdbLink
import eu.darken.sdmse.common.adb.shizuku.PorterGateway
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Binds our privileged host as a user service of the ADB manager's server and hands out one
 * connection per collection. The handshake and the service args are behind [AdbHostLauncherSeam] so
 * this orchestration is unit-testable.
 */
@Reusable
class AdbHostLauncher @Inject constructor(
    private val gateway: PorterGateway,
    private val seam: AdbHostLauncherSeam,
    private val dispatcherProvider: DispatcherProvider,
) {

    fun <Service : IInterface, Host : AdbConnection> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        options: AdbHostOptions,
        connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
        stopTimeoutMs: Long = STOP_TIMEOUT_MS,
    ): Flow<ConnectionWrapper<Service, Host>> = callbackFlow {
        val args = seam.userServiceArgs(hostClass, options)

        // Completed only once a connection was actually handed downstream, this is what the
        // connect watchdog below waits for.
        val ready = CompletableDeferred<Unit>()

        // Started first, so it covers everything up to the first connection: waiting for the previous
        // generation's stop, for a link (a restarting server publishes none for a while), the bind
        // and the handshake.
        val watchdog = launch {
            if (withTimeoutOrNull(connectTimeoutMs) { ready.await() } == null) {
                log(TAG, WARN) { "User service did not connect within ${connectTimeoutMs}ms, closing" }
                // Residual epsilon race: a send() completing concurrently with the deadline can tear
                // down a connection that just came up. The next acquire re-binds.
                close(AdbConnectTimeoutException("ADB user service did not connect within ${connectTimeoutMs}ms"))
            }
        }

        // Written by bindJob, read by the teardown only after bindJob was joined.
        var holdsGenerationLock = false
        var boundLink: AdbLink? = null
        var handshakeJob: Job? = null

        val bindJob = launch {
            GENERATION_LOCK.lock()
            holdsGenerationLock = true

            val link = gateway.link.filterNotNull().first()
            log(TAG) { "Binding user service via $link" }
            boundLink = link

            var connected = false
            try {
                link.userService(args).collect { binder ->
                    if (connected) {
                        // The server re-connected the service with a new binder, the one we
                        // handshaked with is gone.
                        if (close(AdbException("ADB user service reconnected"))) {
                            log(TAG, WARN) { "User service reconnected with a new binder, closing connection" }
                        }
                        return@collect
                    }
                    connected = true
                    // Off the collector: the handshake does binder transactions, and the collection
                    // has to keep observing the service's death meanwhile.
                    handshakeJob = this@callbackFlow.launch(dispatcherProvider.IO) {
                        handshake(binder, serviceClass, options, ready)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "User service bind failed: ${e.asLog()}" }
                close(AdbException("ADB user service bind failed", e))
                return@launch
            }
            // Completed on its own: the service died or the link was lost. Close so the SharedResource
            // generation ends and the next acquire re-binds, instead of handing out a dead connection.
            if (close(AdbException("ADB user service disconnected"))) {
                log(TAG, WARN) { "User service disconnected, closing connection" }
            }
        }

        try {
            log(TAG) { "Waiting for flow to close" }
            awaitClose { log(TAG) { "awaitClose() reached, flow is closing…" } }
        } finally {
            // Runs on cancellation too, so a throw before awaitClose can't leak the user service.
            withContext(NonCancellable) {
                watchdog.cancel()
                bindJob.cancelAndJoin()
                handshakeJob?.cancel()
                try {
                    boundLink?.let { stopUserService(it, args, stopTimeoutMs) }
                } finally {
                    if (holdsGenerationLock) GENERATION_LOCK.unlock()
                }
                log(TAG) { "User service teardown finished." }
            }
        }
    }

    private suspend fun <Service : IInterface, Host : AdbConnection> ProducerScope<ConnectionWrapper<Service, Host>>.handshake(
        binder: IBinder,
        serviceClass: KClass<Service>,
        options: AdbHostOptions,
        ready: CompletableDeferred<Unit>,
    ) {
        try {
            log(TAG) { "Handshaking with the user service, options=$options" }
            val (userConnection, baseConnection) = seam.handshake<Service, Host>(
                binder = binder,
                serviceClass = serviceClass,
                options = options,
            )
            log(TAG) { "Handshake done -> $userConnection" }
            send(ConnectionWrapper(userConnection, baseConnection))
            ready.complete(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "User service handshake failed: ${e.asLog()}" }
            close(AdbException("ADB user service handshake failed", e))
        }
    }

    /**
     * The helper process must not outlive the connection: dropping the binding alone leaves it running.
     *
     * Bounded, because the server may be the reason we are tearing down. A stop that gives up early
     * can still land later and destroy the next generation's helper, which then reconnects.
     */
    private suspend fun stopUserService(
        link: AdbLink,
        args: UserServiceArgs,
        timeoutMs: Long,
    ) {
        log(TAG) { "Stopping user service…" }
        val stopped = try {
            withTimeoutOrNull(timeoutMs) { link.stopUserService(args) }
        } catch (e: Exception) {
            log(TAG, WARN) { "stopUserService() failed: ${e.asLog()}" }
            Unit
        }
        if (stopped == null) log(TAG, WARN) { "stopUserService() did not return within ${timeoutMs}ms" }
    }

    data class ConnectionWrapper<Service : IInterface, Host : AdbConnection>(
        val service: Service,
        val host: Host,
    )

    companion object {
        private val TAG = logTag("ADB", "Host", "Launcher")

        // Every generation binds the same service args, so an older generation's stop would destroy a
        // newer generation's helper. A new generation binds only once the previous one's stop returned
        // or gave up. Process-wide, because the launcher itself is not a singleton.
        private val GENERATION_LOCK = Mutex()

        // How long to wait for a connection, from waiting for a link through the handshake. Generous:
        // a cold AdbHost start is a multi-second affair (see AdbServiceClient's keep-alive rationale),
        // and a server that accepts the bind but never reports the service connected must still end
        // in a failure rather than an eternal spinner.
        internal const val CONNECT_TIMEOUT_MS = 15 * 1000L

        // How long teardown waits for stopUserService() before moving on without it. Short, unlike the
        // connect budget, because it answers a different question: it only decides how long a failing
        // teardown may hold the flow open.
        internal const val STOP_TIMEOUT_MS = 2 * 1000L
    }
}
