package eu.darken.sdmse.common.shizuku.service.internal

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import eu.darken.sdmse.common.shizuku.ShizukuException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import javax.inject.Inject
import kotlin.reflect.KClass

@Reusable
class ShizukuHostLauncher @Inject constructor(
) {

    fun <Binder : Any, Host : ShizukuConnection> createConnection(
        binderClass: KClass<Binder>,
        hostClass: KClass<Host>,
        enableDebug: Boolean = BuildConfigWrap.DEBUG,
        enableTrace: Boolean = false,
        enableDryRun: Boolean = false,
    ) = callbackFlow {
        if (Shizuku.getVersion() < 10) throw IllegalStateException("Shizuku API10+ required")

        val serviceArgs = UserServiceArgs(
            ComponentName(
                BuildConfigWrap.APPLICATION_ID,
                hostClass.qualifiedName!!
            )
        ).apply {
            daemon(false)
            processNameSuffix(logTag("Shizuku"))
            debuggable(enableDebug)
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

                val newOptions = ShizukuHostOptions(
                    isDebug = enableDebug,
                    isTrace = enableTrace,
                    isDryRun = enableDryRun,
                )
                log(TAG) { "Updating host options to $newOptions" }
                baseConnection.updateHostOptions(newOptions)

                val userConnection = baseConnection.userConnection.getInterface(binderClass)
                    ?: throw ShizukuException("Failed to get user connection")

                log(TAG) { "onServiceConnected(...) -> $userConnection" }
                trySendBlocking(userConnection)
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

    companion object {
        private val TAG = logTag("Shizuku", "Host", "Launcher")
    }
}