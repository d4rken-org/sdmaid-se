package eu.darken.sdmse.common.root.javaroot.internal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.*
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

    fun <Binder : Any, Host : RootHost> createConnection(
        binderClass: KClass<Binder>,
        rootHostClass: KClass<Host>,
        enableDebug: Boolean = false,
        useMountMaster: Boolean = false,
    ): Flow<Binder> = callbackFlow {
        log(TAG) { "Initiating connection to host($rootHostClass) via binder($binderClass)" }

        val rootSession = try {
            RxCmdShell.builder().root(true).build().open().blockingGet()
        } catch (e: Exception) {
            throw RootException("Failed to open root session.", e.cause)
        }

        val pairingCode = UUID.randomUUID().toString()

        val ipcReceiver = object : RootIPCReceiver<Binder>(pairingCode, binderClass) {
            override fun onConnect(ipc: Binder) {
                log(TAG) { "onConnect(ipc=$ipc)" }
                trySendBlocking(ipc)
            }

            override fun onDisconnect(ipc: Binder) {
                log(TAG) { "onDisconnect(ipc=$ipc)" }
                close()
            }
        }

        invokeOnClose {
            log(TAG) { "Canceling!" }
            ipcReceiver.release()
            // TODO timeout until we CANCEL?
            cleanUp()
            rootSession.close().subscribe()
        }

        ipcReceiver.connect(context)

        val result = try {
            val cmdBuilder = RootHostCmdBuilder(context, rootHostClass)

            val rootHostOptions = RootHostOptions(
                pairingCode = pairingCode,
                packageName = context.packageName,
                isDebug = enableDebug,
                waitForDebugger = false
            )

            if (useMountMaster) {
                Cmd.builder("su --mount-master").submit(rootSession).observeOn(Schedulers.io()).blockingGet()
            }

            val cmd = cmdBuilder.build(rootHostOptions)
            log { "Launching root host: $rootHostClass" }
            // Doesn't return until root host has quit
            cmd.submit(rootSession).observeOn(Schedulers.io()).blockingGet()

        } catch (e: Exception) {
            throw RootException("Failed to launch java root host.", e.cause)
        }

        log(TAG) { "Root host launch result was: $result" }

        // Check exitcode
        if (result.exitCode == Cmd.ExitCode.SHELL_DIED) {
            throw RootException("Shell died launching the java root host.")
        }
    }

    private fun cleanUp() {

    }

    companion object {
        private val TAG = logTag("Root", "Host", "Launcher")
    }
}