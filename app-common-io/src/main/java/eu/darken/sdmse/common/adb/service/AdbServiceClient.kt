package eu.darken.sdmse.common.adb.service

import eu.darken.sdmse.common.adb.AdbServiceConnection
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.AdbUnavailableException
import eu.darken.sdmse.common.adb.service.internal.AdbConnection
import eu.darken.sdmse.common.adb.service.internal.AdbHostLauncher
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.ipc.FileOpsClient
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsClient
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ipc.ShellOpsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.plus
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbServiceClient @Inject constructor(
    serviceLauncher: AdbHostLauncher,
    @AppScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val adbSettings: AdbSettings,
    private val debugSettings: DebugSettings,
    private val fileOpsClientFactory: FileOpsClient.Factory,
    private val pkgOpsClientFactory: PkgOpsClient.Factory,
    private val shellOpsClientFactory: ShellOpsClient.Factory,
) : SharedResource<AdbServiceClient.Connection>(
    tag = TAG,
    // Run source lifecycle + teardown on IO, not the @AppScope Default pool. On low-core devices the
    // slow host-disconnect IPC during keep-alive expiry would otherwise starve Dispatchers.Default —
    // the same pool the UI's vmScope uses — wedging the dashboard. Mirrors ShellOps/PkgOps.
    parentScope = coroutineScope + dispatcherProvider.IO,
    stopTimeout = ADB_HOST_KEEPALIVE,
    verboseLifecycle = true,
    source = callbackFlow {
        log(TAG) { "Instantiating ADB launcher..." }

        if (adbSettings.useAdb.value() != true) throw AdbUnavailableException("ADB is not enabled")

        val optionsInitial = AdbHostOptions(
            isDebug = debugSettings.isDebugMode.value(),
            isTrace = debugSettings.isTraceMode.value(),
            isDryRun = debugSettings.isDryRunMode.value(),
            recorderPath = debugSettings.recorderPath.value(),
        )

        val lastInternal = MutableStateFlow<AdbConnection?>(null)
        serviceLauncher
            .createServiceHostConnection(optionsInitial)
            .onEach { wrapper ->
                lastInternal.value = wrapper.host
                send(wrapper.service)
            }
            .launchIn(this)

        combine(
            debugSettings.isDebugMode.flow,
            debugSettings.isTraceMode.flow,
            debugSettings.isDryRunMode.flow,
            debugSettings.recorderPath.flow,
            lastInternal.filterNotNull(),
        ) { isDebug, isTrace, isDryRun, recorderPath, lastConnection ->
            val optionsDynamic = AdbHostOptions(
                isDebug = isDebug,
                isTrace = isTrace,
                isDryRun = isDryRun,
                recorderPath = recorderPath,
            )
            log(TAG) { "Updating debug settings: $optionsDynamic" }
            lastConnection.updateHostOptions(optionsDynamic)
        }
            .setupCommonEventHandlers(TAG) { "dynamic-debug-settings" }
            .launchIn(this)

        log(TAG) { "awaitClose()..." }
        awaitClose {
            log(TAG) { "awaitClose() CLOSING" }
        }
    }
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
        val ipc: AdbServiceConnection,
        val clientModules: List<IpcClientModule>
    )

    companion object {

        // Keep the privileged ADB host bound briefly after the last lease is released so in-session
        // re-acquisition (screen-bouncing between dashboard and tools, back-to-back privileged ops)
        // reuses the warm host instead of paying the multi-second Shizuku/ADB cold-start. Bounded on
        // purpose: a uid-2000 host should not linger in the background for long. Teardown still happens
        // (and is prompt/safe); this only delays its start.
        private val ADB_HOST_KEEPALIVE: Duration = Duration.ofSeconds(30)

        fun AdbHostLauncher.createServiceHostConnection(
            options: AdbHostOptions,
        ) = this
            .createConnection(
                serviceClass = AdbServiceConnection::class,
                hostClass = AdbHost::class,
                options = options,
            )
            .onStart { log(TAG) { "Initiating connection to host." } }
            .onEach { log(TAG) { "Connection available: $it" } }
            .catch {
                log(TAG, ERROR) { "Failed to establish connection: ${it.asLog()}" }
                throw AdbUnavailableException("Failed to establish connection", cause = it)
            }
            .onCompletion { log(TAG) { "Connection closed" } }

        internal val TAG = logTag("ADB", "Service", "Client")
    }
}