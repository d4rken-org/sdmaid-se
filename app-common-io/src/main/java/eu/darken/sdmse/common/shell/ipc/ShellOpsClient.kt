package eu.darken.sdmse.common.shell.ipc

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.getRootCause
import eu.darken.sdmse.common.ipc.IpcClientModule
import kotlinx.coroutines.withContext
import java.io.IOException

class ShellOpsClient @AssistedInject constructor(
    @Assisted private val connection: ShellOpsConnection,
    private val dispatcherProvider: DispatcherProvider,
) : IpcClientModule {

    suspend fun execute(cmd: ShellOpsCmd): ShellOpsResult = try {
        withContext(dispatcherProvider.IO) {
            connection.execute(cmd)
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "execute($cmd) failed: ${e.asLog()}" }
        throw fakeIOException(e.getRootCause())
    }

    private fun fakeIOException(e: Throwable): IOException {
        val gulpExceptionPrefix = "java.io.IOException: "
        val message = when {
            e.message.isNullOrEmpty() -> e.toString()
            e.message?.startsWith(gulpExceptionPrefix) == true -> e.message!!.replace(gulpExceptionPrefix, "")
            else -> ""
        }
        return IOException(message, e.cause)
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: ShellOpsConnection): ShellOpsClient
    }

    companion object {
        val TAG = logTag("ShellOps", "Service", "Client")
    }
}