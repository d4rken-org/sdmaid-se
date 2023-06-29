package eu.darken.sdmse.common.root.service.internal

import android.content.Context
import android.os.Debug
import android.os.IInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import eu.darken.sdmse.common.root.RootException
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

        val rootSession = try {
            RxCmdShell.builder().root(true).build().open().blockingGet()
        } catch (e: Exception) {
            throw RootException("Failed to open root session.", e.cause)
        }

        val pairingCode = UUID.randomUUID().toString()

        val ipcReceiver = object : RootConnectionReceiver(pairingCode) {
            override fun onConnect(connection: RootConnection) {
                log(TAG) { "onConnect(connection=$connection)" }

                val userConnection = connection.userConnection.getInterface(serviceClass)
                    ?: throw RootException("Failed to get user connection")

                log(TAG) { "onServiceConnected(...) -> $userConnection" }
                trySendBlocking(ConnectionWrapper(userConnection, connection))
            }

            override fun onDisconnect(ipc: RootConnection) {
                log(TAG) { "onDisconnect(ipc=$ipc)" }
                close()
            }
        }

        invokeOnClose {
            log(TAG) { "Canceling!" }
            ipcReceiver.release()
            // TODO timeout until we CANCEL?
            rootSession.close().subscribe()
        }

        ipcReceiver.connect(context)

        val result = try {
            val cmdBuilder = RootHostCmdBuilder(context, hostClass)

            if (useMountMaster) {
                Cmd.builder("su --mount-master").submit(rootSession).observeOn(Schedulers.io()).blockingGet()
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
                val cmd = cmdBuilder.build(withRelocation = false, initialOptions = initArgs)
                log { "RootHost launch command is $cmd" }

                // Doesn't return until root host has quit
                cmd.submit(rootSession).observeOn(Schedulers.io()).blockingGet()
            } catch (e: Exception) {
                log(TAG, WARN) { "Launch without relocation failed: ${e.asLog()}" }

                val cmd = cmdBuilder.build(withRelocation = true, initialOptions = initArgs)
                log { "RootHost launch command is $cmd" }

                cmd.submit(rootSession).observeOn(Schedulers.io()).blockingGet()
            }
        } catch (e: Exception) {
            throw RootException("Failed to launch java root host.", e.cause)
        }

        log(TAG) { "Root host launch result was: $result" }

        // Check exitcode
        if (result.exitCode == Cmd.ExitCode.SHELL_DIED) {
            throw RootException("Shell died launching the java root host.")
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