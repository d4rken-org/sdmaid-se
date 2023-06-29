package eu.darken.sdmse.common.shizuku.service.internal

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import eu.darken.sdmse.common.shizuku.ShizukuException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import javax.inject.Inject
import kotlin.reflect.KClass

@Reusable
class ShizukuHostLauncher @Inject constructor() {

    fun <Service : IInterface, Host : ShizukuConnection> createConnection(
        serviceClass: KClass<Service>,
        hostClass: KClass<Host>,
        options: ShizukuHostOptions,
    ): Flow<ConnectionWrapper<Service, Host>> = callbackFlow {
        if (Shizuku.getVersion() < 10) throw IllegalStateException("Shizuku API10+ required")

        val serviceArgs = UserServiceArgs(
            ComponentName(
                BuildConfigWrap.APPLICATION_ID,
                hostClass.qualifiedName!!
            )
        ).apply {
            daemon(false)
            processNameSuffix(logTag("Shizuku"))
            debuggable(options.isDebug)
            version(BuildConfigWrap.VERSION_CODE.toInt())
        }

        val callback: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
                log(TAG) { "onServiceConnected($componentName,$binder)" }

                if (binder?.pingBinder() != true) {
                    log(TAG) { "onServiceConnected(...) Invalid binder (ping failed)" }
                    return
                }

                val baseConnection = ShizukuConnection.Stub.asInterface(binder)
                    ?: throw ShizukuException("Failed to get base connection")

                // Initial options, Shizuku has no init arguments through which these can be supplied earlier
                log(TAG) { "Updating host options to $options" }
                baseConnection.updateHostOptions(options)

                val userConnection = baseConnection.userConnection.getInterface(serviceClass)
                    ?: throw ShizukuException("Failed to get user connection")

                log(TAG) { "onServiceConnected(...) -> $userConnection" }
                trySendBlocking(ConnectionWrapper(userConnection, baseConnection as Host))
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                log(TAG, WARN) { "onServiceDisconnected($componentName)" }
            }
        }
        Shizuku.bindUserService(serviceArgs, callback)

        log(TAG) { "Waiting for close" }
        awaitClose {
            Shizuku.unbindUserService(serviceArgs, callback, true)
            log(TAG) { "Flow closed." }
        }
    }

    data class ConnectionWrapper<Service : IInterface, Host : ShizukuConnection>(
        val service: Service,
        val host: Host,
    )


    companion object {
        private val TAG = logTag("Shizuku", "Host", "Launcher")
    }
}