package eu.darken.sdmse.common.adb.service.internal

import android.os.IBinder
import android.os.IInterface
import dagger.Reusable
import eu.darken.sdmse.common.adb.AdbConnectTimeoutException
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.coroutine.runDetachedWithTimeout
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
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
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    @OptIn(DelicateCoroutinesApi::class) // isClosedForSend, to skip the close() on intentional teardown
    fun <Service : IInterface, Host : AdbConnection> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        options: AdbHostOptions,
        connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
        apiVersionTimeoutMs: Long = API_VERSION_TIMEOUT_MS,
        unbindTimeoutMs: Long = UNBIND_TIMEOUT_MS,
    ): Flow<ConnectionWrapper<Service, Host>> = callbackFlow {
        // Shizuku.getVersion() only returns a cached field once the server has pushed its version via
        // bindApplication(); until then it is a synchronous binder transaction that wedges against an
        // unresponsive server. This runs before the connect watchdog below is armed, so it needs its
        // own bound. Detached: a wedged binder thread leaks rather than pinning every collector.
        val apiVersion = appScope.runDetachedWithTimeout(dispatcherProvider.IO, apiVersionTimeoutMs) {
            serviceFactory.apiVersion()
        } ?: throw AdbConnectTimeoutException("Shizuku getVersion() did not respond within ${apiVersionTimeoutMs}ms")
        if (apiVersion < 10) throw IllegalStateException("Shizuku API10+ required")

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
            if (withTimeoutOrNull(connectTimeoutMs) { ready.await() } == null) {
                log(TAG, WARN) { "User service did not connect within ${connectTimeoutMs}ms, closing" }
                // Residual epsilon race: a send() completing concurrently with the deadline can tear
                // down a connection that just came up. CompletableDeferred + withTimeoutOrNull narrows
                // that window but can't close it; the next acquire re-binds.
                close(AdbConnectTimeoutException("Shizuku user service did not connect within ${connectTimeoutMs}ms"))
            }
        }

        // bindUserService() is a synchronous binder transaction that can wedge indefinitely
        // (same upstream Shizuku defect family as the never-firing callback). It must run OUTSIDE
        // the producer scope: channelFlow collection awaits all producer children, so a wedged
        // bind launched in this scope would keep the watchdog's close() from ever releasing
        // waiting collectors - exactly the eternal setup spinner this launcher guards against.
        //
        // Unbind ownership is handed over via CAS so no interleaving of (bind returns) and
        // (teardown gives up waiting) can leak the binding or unbind twice: whoever loses the
        // race for the state transition owns the cleanup.
        val bindState = AtomicReference(BindState.BINDING)
        val bindJob = appScope.launch(dispatcherProvider.IO) {
            try {
                service.bind()
            } catch (e: Exception) {
                // Synchronous bind failures keep propagating to collectors, like they did when
                // bind() ran in the producer. State stays BINDING: there is nothing to unbind.
                close(e)
                return@launch
            }
            if (!bindState.compareAndSet(BindState.BINDING, BindState.BOUND)) {
                // Teardown already claimed TEARDOWN while bind() was underway and skipped the
                // unbind; without this late unbind the binding would leak.
                log(TAG, WARN) { "bind() returned after teardown gave up waiting, unbinding late" }
                runCatching { service.unbind() }
                    .onFailure { log(TAG, WARN) { "Late unbindUserService() failed: ${it.asLog()}" } }
            }
        }

        try {
            log(TAG) { "Waiting for flow to close" }
            awaitClose { log(TAG) { "awaitClose() reached, flow is closing…" } }
        } finally {
            // Runs on cancellation too. Mirrors RootHostLauncher: cleanup lives in the finally (not
            // only in awaitClose) so a throw before awaitClose can't leak the Shizuku binding, and
            // unbind is best-effort so a DeadObjectException can't mask the cancellation.
            withContext(NonCancellable) {
                // Bounded settle so a merely-slow (not wedged) bind() still gets its unbind here.
                withTimeoutOrNull(BIND_SETTLE_TIMEOUT_MS) { bindJob.join() }
                if (bindState.compareAndSet(BindState.BINDING, BindState.TEARDOWN)) {
                    // bind() has not returned yet: nothing to unbind now, the late-unbind path
                    // above owns the eventual cleanup.
                } else if (bindState.get() == BindState.BOUND) {
                    log(TAG) { "Unbinding Shizuku user service…" }
                    // unbindUserService() is a synchronous binder transaction against the same server
                    // that may already be wedged, and runCatching bounds nothing - it only catches a
                    // throw. Collection of a callbackFlow awaits its producer, so an unbounded wedge
                    // in this finally pins every collector even though the watchdog above already
                    // close()d the channel: the eternal setup spinner, moved into teardown. Detached
                    // + bounded, the same trade bind() makes above.
                    //
                    // That trade is not free, and not merely "the unbind finishes later": once we stop
                    // waiting, an outstanding unbind can outlive this generation, and Shizuku keys its
                    // remove=true unbind on the service args rather than on our callback - so a late
                    // one can remove a service a NEWER generation just bound. The late-unbind path
                    // above already carries that race; a teardown that never returns is worse. The
                    // detached call may also never run at all, if it was still queued when we gave up.
                    runCatching {
                        val unbound = appScope.runDetachedWithTimeout(dispatcherProvider.IO, unbindTimeoutMs) {
                            runCatching { service.unbind() }
                                .onFailure { log(TAG, WARN) { "unbindUserService() failed: ${it.asLog()}" } }
                            Unit
                        }
                        if (unbound == null) {
                            log(TAG, WARN) { "unbindUserService() did not return within ${unbindTimeoutMs}ms" }
                        }
                    }.onFailure {
                        // Mainly @AppScope already cancelled (shutdown): await() then throws a
                        // CancellationException that withTimeoutOrNull does not convert. It would not
                        // corrupt what collectors see (the watchdog's close() already latched the
                        // cause), but it would abandon the rest of this teardown - the disconnect wait
                        // below - and surface as an unhandled producer failure. Teardown is
                        // best-effort, and not being able to start the unbind is no reason to skip
                        // the rest of it.
                        log(TAG, WARN) { "Detached unbind could not run: ${it.asLog()}" }
                    }
                    // Bounded wait for the actual disconnect; without it, quick flow restarts can
                    // cause DeadObjectExceptions from our Shizuku service binder. Still worth doing
                    // after a timed-out unbind: the server may have processed the removal while the
                    // synchronous transaction was still waiting on its reply.
                    withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { service.awaitDisconnect() }
                    log(TAG) { "Shizuku user service unbind teardown finished." }
                }
            }
        }
    }

    private enum class BindState {
        /** bind() is still running (or failed, leaving nothing to clean up). */
        BINDING,

        /** bind() returned and the flow's teardown owns the unbind. */
        BOUND,

        /** Teardown ran while bind() was still wedged; the bind job owns a late unbind. */
        TEARDOWN,
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

        // How long teardown waits for a still-running bind() before concluding it is wedged and
        // leaving cleanup to the late-unbind path.
        private const val BIND_SETTLE_TIMEOUT_MS = 500L

        // How long to wait for onServiceConnected after binding. Generous: a cold AdbHost start is a
        // multi-second affair (see AdbServiceClient's keep-alive rationale). AppOpsNext uses 12s for
        // the same probe against the same upstream Shizuku defect where bindUserService() returns but
        // the connection callback never fires (MediaTek/HyperOS, Shizuku 13.6.0).
        internal const val CONNECT_TIMEOUT_MS = 15 * 1000L

        // How long teardown waits for unbindUserService() before moving on without it. Short, unlike
        // the probe timeouts around it, because it answers a different question: a probe that gives up
        // too early reports a working Shizuku as broken, while this one only decides how long a
        // failing teardown may hold the flow open.
        //
        // Measured against a healthy Shizuku, idle, 4 cold teardowns each: Pixel 7a (2023) 4.1-6.1ms,
        // Pixel 2 (2017) 6.5-10.8ms. So this leaves ~185x headroom over the slowest sample across a
        // six-year hardware gap. Deliberately not tightened towards those numbers: the measurements
        // are from idle devices, while the wedge this guards against shows up on low-end hardware
        // under memory pressure, where a dispatcher hop plus binder round-trip is far slower. Giving
        // up early is not free either - it is what exposes the cross-generation race described at the
        // call site - so the headroom is the point.
        internal const val UNBIND_TIMEOUT_MS = 2 * 1000L

        // Budget for the getVersion() round-trip. Same size as CONNECT_TIMEOUT_MS on purpose: this only
        // has to turn "never returns" into "eventually fails", and timing out a slow-but-working
        // Shizuku would break a setup that currently works.
        internal const val API_VERSION_TIMEOUT_MS = 15 * 1000L
    }
}
