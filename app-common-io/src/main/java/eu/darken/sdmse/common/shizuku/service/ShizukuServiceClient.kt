package eu.darken.sdmse.common.shizuku.service

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.ipc.FileOpsClient
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsClient
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ipc.ShellOpsClient
import eu.darken.sdmse.common.shizuku.ShizukuServiceConnection
import eu.darken.sdmse.common.shizuku.ShizukuSettings
import eu.darken.sdmse.common.shizuku.ShizukuUnavailableException
import eu.darken.sdmse.common.shizuku.service.internal.ShizukuHostLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuServiceClient @Inject constructor(
    serviceLauncher: ShizukuHostLauncher,
    @AppScope coroutineScope: CoroutineScope,
    private val shizukuSettings: ShizukuSettings,
    private val fileOpsClientFactory: FileOpsClient.Factory,
    private val pkgOpsClientFactory: PkgOpsClient.Factory,
    private val shellOpsClientFactory: ShellOpsClient.Factory,
) : SharedResource<ShizukuServiceClient.Connection>(
    TAG,
    coroutineScope,
    flow {
        log(TAG) { "Instantiating Shizuku launcher..." }

        if (shizukuSettings.isEnabled.value() != true) throw RootUnavailableException("Shizuku is not enabled")

        emit(serviceLauncher.createServiceHostConnection())
    }
        .flattenConcat()
        .map {
            Connection(
                ipc = it,
                clientModules = listOf(
                    fileOpsClientFactory.create(it.fileOps),
                    pkgOpsClientFactory.create(it.pkgOps),
                    shellOpsClientFactory.create(it.shellOps),
                )
            )
        }
) {

    data class Connection(
        val ipc: ShizukuServiceConnection,
        val clientModules: List<IpcClientModule>
    ) {
        inline fun <reified T> getModule(): T = clientModules.single { it is T } as T
    }

    suspend fun <T> runSessionAction(action: suspend (Connection) -> T): T = get().use {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "runSessionAction(action=$action)" }
        action(it.item)
    }

    companion object {

        fun ShizukuHostLauncher.createServiceHostConnection(): Flow<ShizukuServiceConnection> = this
            .createConnection(
                binderClass = ShizukuServiceConnection::class,
                hostClass = ShizukuHost::class,
                enableDebug = Bugs.isDebug,
                enableTrace = Bugs.isTrace,
            )
            .onStart { log(TAG) { "Initiating connection to host." } }
            .onEach { log(TAG) { "Connection available: $it" } }
            .catch {
                log(TAG, ERROR) { "Failed to establish connection: ${it.asLog()}" }
                throw ShizukuUnavailableException("Failed to establish connection", cause = it)
            }
            .onCompletion { log(TAG) { "Connection unavailable." } }

        internal val TAG = logTag("Shizuku", "Service", "Client")
    }
}