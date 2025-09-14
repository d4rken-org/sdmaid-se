package eu.darken.sdmse.common.shell

import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.AdbUnavailableException
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.adb.service.runModuleAction
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourcesAlive
import eu.darken.sdmse.common.shell.ipc.ShellOpsClient
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.shell.ipc.ShellOpsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellOps @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> adbOps(action: suspend (ShellOpsClient) -> T): T {
        if (!adbManager.canUseAdbNow()) throw AdbUnavailableException()
        return keepResourcesAlive(adbManager.serviceClient) {
            adbManager.serviceClient.runModuleAction(ShellOpsClient::class.java) { action(it) }
        }
    }

    private suspend fun <T> rootOps(action: suspend (ShellOpsClient) -> T): T {
        if (!rootManager.canUseRootNow()) throw RootUnavailableException()
        return keepResourcesAlive(rootManager.serviceClient) {
            rootManager.serviceClient.runModuleAction(ShellOpsClient::class.java) { action(it) }
        }
    }

    suspend fun execute(cmd: ShellOpsCmd, mode: Mode): ShellOpsResult = withContext(dispatcherProvider.IO) {
        try {
            var result: ShellOpsResult? = null
            if (mode == Mode.NORMAL) {
                log(TAG, VERBOSE) { "execute(mode->NORMAL): $cmd" }
                result = cmd.toFlowCmd().execute().toShellOpsResult()
            }

            if (result == null && rootManager.canUseRootNow() && mode == Mode.ROOT) {
                log(TAG, VERBOSE) { "execute(mode->ROOT): $cmd" }
                result = rootOps { it.execute(cmd) }
            }

            if (result == null && adbManager.canUseAdbNow() && mode == Mode.ADB) {
                log(TAG, VERBOSE) { "execute(mode->ADB): $cmd" }
                result = adbOps { it.execute(cmd) }
            }

            if (Bugs.isTrace) {
                log(TAG, VERBOSE) { "execute($cmd, $mode): $result" }
            }

            if (result == null) throw ShellOpsException("No matching mode", cmd)

            result
        } catch (e: IOException) {
            log(TAG, WARN) { "execute($cmd) failed: ${e.asLog()}" }
            throw ShellOpsException(cmd = cmd, cause = e)
        }
    }

    private fun ShellOpsCmd.toFlowCmd() = FlowCmd(cmds)

    private fun FlowCmd.Result.toShellOpsResult() = ShellOpsResult(
        exitCode = exitCode.value,
        output = output,
        errors = errors
    )

    enum class Mode {
        NORMAL, ROOT, ADB
    }

    companion object {
        val TAG = logTag("ShellOps")
    }
}