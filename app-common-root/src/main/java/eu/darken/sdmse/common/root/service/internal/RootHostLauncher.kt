package eu.darken.sdmse.common.root.service.internal

import android.content.Context
import android.os.Debug
import android.os.IInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.FlowCmdShell
import eu.darken.flowshell.core.cmd.execute
import eu.darken.flowshell.core.cmd.openSession
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import eu.darken.sdmse.common.root.RootException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Based on https://github.com/Chainfire/librootjava
 * Also see
 * https://github.com/Mygod/VPNHotspot/blob/master/mobile/src/main/java/be/mygod/librootkotlinx/AppProcess.kt
 * https://github.com/Chainfire/librootjava/blob/master/librootjava/src/main/java/eu/chainfire/librootjava/AppProcess.java
 * https://github.com/zhanghai/MaterialFiles/tree/71e1e0d50573d5c3645e0fe7ec025e0ec75024ec/app/src/main/java/me/zhanghai/android/files/provider/root
 * https://github.com/RikkaApps/Shizuku/blob/master/starter/src/main/java/moe/shizuku/starter/ServiceStarter.java
 */
class RootHostLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun <Service : IInterface, Host : BaseRootHost> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        useMountMaster: Boolean = false,
        options: RootHostOptions,
    ): Flow<ConnectionWrapper<Service>> = callbackFlow {
        log(TAG) { "createConnection($serviceClass, $hostClass, $useMountMaster, $options)" }
        log(TAG, INFO) { "Initiating connection to host($hostClass) via binder($serviceClass)" }

        val (rootSession, _) = try {
            FlowCmdShell("su").openSession(this)
        } catch (e: Exception) {
            throw RootException("Failed to open root session.", e.cause)
        }

        val pairingCode = UUID.randomUUID().toString()

        val ipcReceiver = object : RootConnectionReceiver(pairingCode) {
            override fun onConnect(connection: RootConnection) {
                log(TAG) { "onConnect(connection=$connection)" }

                val userConnection = try {
                    connection.userConnection.getInterface(serviceClass) as Service
                } catch (e: Exception) {
                    close(RootException("Failed to get user connection (ROOT)", e))
                    return
                }

                log(TAG) { "onServiceConnected(...) -> $userConnection" }
                trySendBlocking(ConnectionWrapper(userConnection, connection))
            }

            override fun onDisconnect(connection: RootConnection) {
                log(TAG) { "onDisconnect(ipc=$connection)" }
                close()
            }
        }

        ipcReceiver.connect(context)

        launch {
            try {
                val cmdBuilder = RootHostCmdBuilder(context, hostClass)

                if (useMountMaster) {
                    FlowCmd("su --mount-master").execute(rootSession)
                }

                val initArgs = RootHostInitArgs(
                    pairingCode = pairingCode,
                    packageName = context.packageName,
                    waitForDebugger = options.isTrace && Debug.isDebuggerConnected(),
                    isDebug = options.isDebug,
                    isTrace = options.isTrace,
                    isDryRun = options.isDryRun,
                    recorderPath = options.recorderPath
                )

                try {
                    val cmd = cmdBuilder.build(withRelocation = false, initialOptions = initArgs).also {
                        log { "RootHost launch command is $it" }
                    }

                    // Doesn't return until root host has quit
                    cmd.execute(rootSession).also {
                        log(TAG) { "Session (WITH-relocation) has ended: $it" }
                    }
                } catch (e: CancellationException) {
                    log(TAG, DEBUG) { "Session was cancelled: ${e.asLog()}" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Launch without relocation failed: ${e.asLog()}" }

                    val cmd = cmdBuilder.build(withRelocation = true, initialOptions = initArgs).also {
                        log { "RootHost launch command is $it" }
                    }

                    cmd.execute(rootSession).also {
                        log(TAG) { "Session (without-relocation) has ended: $it" }
                    }
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Launch completely failed failed: ${e.asLog()}" }
                throw RootException("Failed to launch java root host.", e.cause)
            }

            log(TAG, INFO) { "Root host has quit" }
        }

        log(TAG) { "Waiting for session to close..." }
        awaitClose {
            log(TAG) { "Session is closing..." }
            ipcReceiver.release()

            runBlocking {
                withTimeoutOrNull(10 * 1000) { rootSession.close() } ?: rootSession.cancel()
            }
        }
    }


    data class ConnectionWrapper<Service : IInterface>(
        val service: Service,
        val host: RootConnection,
    )

    companion object {
        private val TAG = logTag("Root", "Host", "Launcher")
    }
}