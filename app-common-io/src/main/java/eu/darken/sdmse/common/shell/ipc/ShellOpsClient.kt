package eu.darken.sdmse.common.shell.ipc

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.IpcClientModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ShellOpsClient @AssistedInject constructor(
    @Assisted private val connection: ShellOpsConnection,
    private val dispatcherProvider: DispatcherProvider,
) : IpcClientModule {

    suspend fun execute(cmd: ShellOpsCmd): ShellOpsResult = try {
        withContext(dispatcherProvider.IO) {
            connection.execute(cmd)
        }
    } catch (e: Exception) {
        throw e.refineException().also {
            log(TAG, ERROR) { "execute($cmd) failed: ${it.asLog()}" }
        }
    }

    fun executeStream(cmd: ShellOpsCmd): Flow<ShellOpsStreamEvent> = flow {
        val remote = connection.executeStream(cmd)
        remote.toShellOpsEventFlow().collect { event ->
            when (event) {
                is ShellOpsStreamEvent.Error -> throw Exception(event.message)
                else -> emit(event)
            }
        }
    }.catch { cause ->
        log(TAG, ERROR) { "executeStream($cmd) failed: ${cause.asLog()}" }
        throw cause.refineException()
    }.flowOn(dispatcherProvider.IO)

    @AssistedFactory
    interface Factory {
        fun create(connection: ShellOpsConnection): ShellOpsClient
    }

    companion object {
        val TAG = logTag("ShellOps", "Service", "Client")
    }
}
