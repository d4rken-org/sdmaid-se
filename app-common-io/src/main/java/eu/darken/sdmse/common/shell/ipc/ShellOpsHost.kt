package eu.darken.sdmse.common.shell.ipc

import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.IpcHostModule
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


class ShellOpsHost @Inject constructor(
    @AppScope scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : ShellOpsConnection.Stub(), IpcHostModule {

    private val sharedShell = SharedShell(TAG, scope + dispatcherProvider.Default)

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

    companion object {
        val TAG = logTag("ShellOps", "Service", "Host", Bugs.processTag)
    }

}