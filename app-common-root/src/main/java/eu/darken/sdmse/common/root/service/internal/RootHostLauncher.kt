package eu.darken.sdmse.common.root.service.internal

import android.os.IInterface
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import eu.darken.sdmse.common.root.RootException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Based on https://github.com/Chainfire/librootjava
 * Also see
 * https://github.com/Mygod/VPNHotspot/blob/master/mobile/src/main/java/be/mygod/librootkotlinx/AppProcess.kt
 * https://github.com/Chainfire/librootjava/blob/master/librootjava/src/main/java/eu/chainfire/librootjava/AppProcess.java
 * https://github.com/zhanghai/MaterialFiles/tree/71e1e0d50573d5c3645e0fe7ec025e0ec75024ec/app/src/main/java/me/zhanghai/android/files/provider/root
 * https://github.com/RikkaApps/Shizuku/blob/master/starter/src/main/java/moe/shizuku/starter/ServiceStarter.java
 *
 * The OS/root touchpoints (session, IPC receiver, command building) are behind injectable seams
 * ([RootSessionFactory], [RootIpcReceiverFactory], [RootLaunchCommandFactory]) so this orchestration
 * — especially the finally-block teardown ordering — is unit-testable. See RootHostLauncherSeam.kt.
 */
class RootHostLauncher @Inject constructor(
    private val sessionFactory: RootSessionFactory,
    private val receiverFactory: RootIpcReceiverFactory,
    private val commandFactory: RootLaunchCommandFactory,
) {

    fun <Service : IInterface, Host : BaseRootHost> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        useMountMaster: Boolean = false,
        options: RootHostOptions,
    ): Flow<ConnectionWrapper<Service>> = callbackFlow {
        val iTag = "$TAG:${UUID.randomUUID().toString().takeLast(4)}"
        log(iTag, INFO) { "createConnection($serviceClass, $hostClass, $useMountMaster, $options)" }

        val pairingCode = UUID.randomUUID().toString()
        val connected = AtomicBoolean(false)

        val ipcReceiver = receiverFactory.create(
            pairingCode = pairingCode,
            onConnect = fun(connection: RootConnection) {
                log(iTag) { "onConnect(connection=$connection), getting our interface..." }
                val userConnection = try {
                    connection.userConnection.getInterface(serviceClass) as Service
                } catch (e: Exception) {
                    log(iTag, ERROR) { "Failed to get our interface: ${e.asLog()}" }
                    close(RootException("Failed to get user connection (ROOT)", e))
                    return
                }

                log(iTag, INFO) { "onServiceConnected(...) got our interface: $userConnection" }
                connected.set(true)
                trySendBlocking(ConnectionWrapper(userConnection, connection))
            },
            onDisconnect = { connection ->
                log(iTag, INFO) { "onDisconnect(ipc=$connection), closing channel..." }
                close()
                log(iTag, VERBOSE) { "onDisconnect(ipc=$connection), channel closed" }
            },
        )

        // Everything from receiver registration onwards is inside the try so the finally always runs
        // the cleanup — even if connect()/open() throws (otherwise the receiver or root session could
        // leak) or if we're cancelled while parked in execute() below (the common case — see finally).
        var rootSession: RootSession? = null
        try {
            log(iTag) { "Initiating connection to host($hostClass) via binder($serviceClass)" }
            ipcReceiver.connect()

            rootSession = try {
                sessionFactory.open(this)
            } catch (e: Exception) {
                throw RootException("Failed to open root session.", e)
            }

            if (useMountMaster) {
                try {
                    log(iTag, INFO) { "Using --mount-master" }
                    val result = rootSession.execute(FlowCmd("su --mount-master"))
                    log(iTag) { "--mount-master result: $result" }
                    if (!result.isSuccessful) throw IllegalStateException("--mount-master command was unsuccessful")
                } catch (e: Exception) {
                    log(iTag) { "Failed to use --mount-master" }
                }
            }

            val command = commandFactory.create(hostClass, pairingCode, options)

            // Attempt 1: direct exec. May either block until the root host process exits
            // (success path, with onConnect having fired in the meantime) or fail fast on
            // a broken pipe / unsupported exec. Use `connected` rather than the throw/
            // return result to decide whether the host actually bound.
            try {
                val cmd = command.build(withRelocation = false)
                log(iTag) { "Launching root host with command: $cmd" }
                rootSession.execute(cmd).also {
                    log(iTag) { "Session (no-relocation) ended: $it" }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log(iTag, WARN) { "Launch without relocation failed: ${e.asLog()}" }
            }

            // Attempt 2: relocation. Only needed when the direct exec didn't actually
            // bring up the binder (some Android versions/SELinux policies refuse to
            // execute /proc/<pid>/exe in-place).
            if (!connected.get()) {
                log(iTag, INFO) { "No binder yet, retrying root host launch with relocation" }
                try {
                    val cmd = command.build(withRelocation = true)
                    log(iTag) { "Launching root host (relocation) with command: $cmd" }
                    rootSession.execute(cmd).also {
                        log(iTag) { "Session (relocation) ended: $it" }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log(iTag, WARN) { "Launch WITH relocation failed too: ${e.asLog()}" }
                }
            }

            // If neither attempt produced a binder connection, fail the callbackFlow so
            // downstream SharedResource consumers (e.g. RootManager.isRooted via
            // AutomationSetupModule.state) stop waiting on the Loading state forever.
            if (!connected.get()) {
                log(iTag, WARN) { "All launch attempts finished without a binder connection" }
                close(RootException("Root host did not bind after exec/relocation attempts"))
            }

            log(iTag, VERBOSE) { "Reached awaitClose" }
            awaitClose {
                log(iTag) { "Session is closing (awaitClose reached)…" }
            }
        } finally {
            // Runs on cancellation too — including when we're cancelled while still parked in
            // execute() above, which is where the producer spends the whole connection. The previous
            // teardown lived in awaitClose {}, which is unreachable in that case, so the host never
            // got told to disconnect and waited (RootIPC.byeWaiter, no timeout) until binder death —
            // minutes on a frozen-but-alive app.
            withContext(NonCancellable) {
                log(iTag) { "Cleaning up root host connection…" }
                // bye() FIRST so the host's RootIPC.byeWaiter wakes immediately.
                runCatching { ipcReceiver.release() }
                    .onFailure { log(iTag, WARN) { "ipcReceiver.release() failed: ${it.asLog()}" } }

                rootSession?.let { session ->
                    // session.close() writes `exit` and awaits exit (cancellable coroutine wait, so
                    // the coroutine timeout bounds it). Fall back to a forceful cancel() otherwise.
                    val closed = withTimeoutOrNull(SESSION_CLOSE_TIMEOUT_MS) {
                        runCatching { session.close() }.isSuccess
                    }
                    if (closed != true) {
                        log(iTag) { "Graceful session close did not finish, cancelling…" }
                        runCatching { session.cancel() }
                            .onFailure { log(iTag, WARN) { "session.cancel() failed: ${it.asLog()}" } }
                    }
                }
                log(iTag, VERBOSE) { "Cleanup done" }
            }
        }
    }

    data class ConnectionWrapper<Service : IInterface>(
        val service: Service,
        val host: RootConnection,
    )

    companion object {
        private val TAG = logTag("Root", "Host", "Launcher")

        // How long to wait for a graceful session close (write `exit` + await) before forcefully
        // cancelling/killing the root session shell.
        private const val SESSION_CLOSE_TIMEOUT_MS = 10 * 1000L
    }
}
