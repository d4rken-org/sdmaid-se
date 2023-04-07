package eu.darken.sdmse.common.root.service

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.root.FileOpsClient
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsClient
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.root.service.internal.RootHostLauncher
import eu.darken.sdmse.common.shell.root.ShellOpsClient
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class RootServiceHostLauncher @Inject constructor(
    private val rootHostLauncher: RootHostLauncher,
    private val fileOpsClientFactory: FileOpsClient.Factory,
    private val pkgOpsClientFactory: PkgOpsClient.Factory,
    private val shellOpsClientFactory: ShellOpsClient.Factory,
) {

    fun create(
        /**
         * TODO Keep this false, but evaluate more types of rooted devices
         * Not needed unless [DataAreaManager] fails to get the altered paths or the rest of IO can't cope.
         * Being able to work without mount-master is more reliable.
         */
        useMountMaster: Boolean = false
    ): Flow<RootServiceClient.Connection> = rootHostLauncher
        .createConnection(
            binderClass = RootServiceConnection::class,
            rootHostClass = RootServiceHost::class,
            enableDebug = Bugs.isDebug,
            enableTrace = Bugs.isTrace,
            useMountMaster = useMountMaster,
        )
        .onStart { log(TAG) { "Initiating connection to host." } }
        .map { ipc ->
            RootServiceClient.Connection(
                ipc = ipc,
                clientModules = listOf(
                    fileOpsClientFactory.create(ipc.fileOps),
                    pkgOpsClientFactory.create(ipc.pkgOps),
                    shellOpsClientFactory.create(ipc.shellOps),
                )
            )
        }
        .onEach { log(TAG) { "Connection available: $it" } }
        .catch {
            log(TAG, ERROR) { "Failed to establish connection: ${it.asLog()}" }
            throw RootUnavailableException("Failed to establish connection", cause = it)
        }
        .onCompletion { log(TAG) { "Connection unavailable." } }

    companion object {
        private val TAG = logTag("Root", "Service", "Host", "Launcher")
    }
}