package eu.darken.sdmse.common.adb.service.internal

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import javax.inject.Inject
import kotlin.reflect.KClass

@Reusable
class AdbHostLauncher @Inject constructor() {

    fun <Service : IInterface, Host : AdbConnection> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        options: AdbHostOptions,
    ): Flow<ConnectionWrapper<Service, Host>> = callbackFlow {
        if (Shizuku.getVersion() < 10) throw IllegalStateException("Shizuku API10+ required")

        val serviceArgs = UserServiceArgs(
            ComponentName(
                BuildConfigWrap.APPLICATION_ID,
                hostClass.qualifiedName!!
            )
        ).apply {
            daemon(false)
            processNameSuffix(logTag("ADB"))
            debuggable(options.isDebug)
            version(BuildConfigWrap.VERSION_CODE.toInt())
        }

        val awaitDisconnect = CompletableDeferred<Unit>()

        val callback: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
                log(TAG) { "onServiceConnected($componentName,$binder)" }

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
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                log(TAG) { "onServiceDisconnected($componentName)" }
                awaitDisconnect.complete(Unit)
            }
        }
        Shizuku.bindUserService(serviceArgs, callback)

        log(TAG) { "Waiting for flow to close" }
        awaitClose {
            Shizuku.unbindUserService(serviceArgs, callback, true)
            log(TAG) { "Waiting for disconnect..." }
            // If we don't wait for the service to actually disconnect,
            // then quick flow restarts can cause DeadObjectExceptions being thrown by our Shizuku service binder
            runBlocking {
                withTimeoutOrNull(500) { awaitDisconnect.await() }
            }

            log(TAG) { "Flow closed." }
        }
    }

    data class ConnectionWrapper<Service : IInterface, Host : AdbConnection>(
        val service: Service,
        val host: Host,
    )

    companion object {
        private val TAG = logTag("ADB", "Host", "Launcher")
    }
}