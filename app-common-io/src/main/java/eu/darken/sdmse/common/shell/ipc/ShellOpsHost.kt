package eu.darken.sdmse.common.shell.ipc

import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.IpcHostModule
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


class ShellOpsHost @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ShellOpsConnection.Stub(), IpcHostModule {

    private val sharedShell = SharedShell(TAG, appScope + dispatcherProvider.Default)

    override fun execute(cmd: ShellOpsCmd): ShellOpsResult = try {
        runBlocking {
            val result = sharedShell.useRes {
                FlowCmd(cmd.cmds).execute(it)
            }
            ShellOpsResult(
                exitCode = result.exitCode.value,
                output = result.output,
                errors = result.errors
            )
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "execute(cmd=$cmd) failed." }
        throw e.wrapToPropagate()
    }

    override fun executeStream(cmd: ShellOpsCmd): RemoteInputStream {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "executeStream(cmd=$cmd)" }
        val eventFlow: Flow<ShellOpsStreamEvent> = flow {
            try {
                val result = sharedShell.useRes {
                    FlowCmd(cmd.cmds).execute(it)
                }
                result.output.forEach { emit(ShellOpsStreamEvent.Stdout(it)) }
                result.errors.forEach { emit(ShellOpsStreamEvent.Stderr(it)) }
                emit(ShellOpsStreamEvent.Exit(result.exitCode.value))
            } catch (e: Exception) {
                log(TAG, ERROR) { "executeStream(cmd=$cmd) failed: ${e.asLog()}" }
                val wrapped = e.wrapToPropagate()
                emit(ShellOpsStreamEvent.Error(wrapped.message ?: e.toString()))
            }
        }
        return eventFlow.toRemoteInputStream(appScope + dispatcherProvider.Default)
    }

    companion object {
        val TAG = logTag("ShellOps", "Service", "Host", Bugs.processTag)
    }

}
