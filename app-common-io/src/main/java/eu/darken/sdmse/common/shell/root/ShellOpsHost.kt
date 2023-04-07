package eu.darken.sdmse.common.shell.root

import eu.darken.rxshell.cmd.Cmd
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


class ShellOpsHost @Inject constructor(
    @AppScope scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : ShellOpsConnection.Stub() {

    private val sharedShell = SharedShell(TAG, scope + dispatcherProvider.Default)

    override fun execute(cmd: ShellOpsCmd): ShellOpsResult = try {
        runBlocking {
            val result = sharedShell.useRes {
                Cmd.builder(cmd.cmds).execute(it)
            }
            ShellOpsResult(
                exitCode = result.exitCode,
                output = result.output,
                errors = result.errors
            )
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "execute(cmd=$cmd) failed." }
        throw wrapPropagating(e)
    }

    private fun wrapPropagating(e: Exception): Exception {
        return if (e is UnsupportedOperationException) e
        else UnsupportedOperationException(e)
    }

    companion object {
        val TAG = logTag("Root", "Service", "ShellOps", "Host")
    }

}