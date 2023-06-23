package eu.darken.sdmse.common.shell

import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.root.useRootNow
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
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
    private val rootServiceClient: RootServiceClient,
    private val rootManager: RootManager,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> rootOps(action: suspend ShellOpsClient.() -> T): T {
        return rootServiceClient.runModuleAction(ShellOpsClient::class.java) { action(it) }
    }

    private suspend fun <T> runIO(
        block: suspend CoroutineScope.() -> T
    ): T = withContext(dispatcherProvider.IO) {
        block()
    }

    suspend fun hasRoot(): Boolean = rootManager.useRootNow()

    suspend fun execute(cmd: ShellOpsCmd, mode: Mode = Mode.AUTO): ShellOpsResult = runIO {
        try {
            var result: ShellOpsResult? = null
            if (mode == Mode.NORMAL || mode == Mode.AUTO) {
                log(TAG, VERBOSE) { "execute(mode->NORMAL): $cmd" }
                result = cmd.toRxCmdBuilder()
                    .execute(RxCmdShell.builder().build())
                    .toShellOpsResult()
            }

            if (result == null && hasRoot() && (mode == Mode.ELEVATED || mode == Mode.ROOT || mode == Mode.AUTO)) {
                log(TAG, VERBOSE) { "execute(mode->ROOT): $cmd" }
                result = rootOps { execute(cmd) }
            }

            if (Bugs.isTrace) {
                log(TAG, VERBOSE) { "execute($cmd, $mode): $result" }
            }

            if (result == null) throw ShellOpsException(cmd, "No matching mode")

            result
        } catch (e: IOException) {
            log(TAG, Logging.Priority.WARN) { "execute($cmd) failed: ${e.asLog()}" }
            throw ShellOpsException(cmd, cause = e)
        }
    }

    private fun ShellOpsCmd.toRxCmdBuilder() = Cmd.builder(cmds)

    private fun Cmd.Result.toShellOpsResult() = ShellOpsResult(
        exitCode = exitCode,
        output = output,
        errors = errors
    )

    enum class Mode {
        AUTO, ELEVATED, NORMAL, ROOT
    }

    companion object {
        val TAG = logTag("ShellOps")
    }
}