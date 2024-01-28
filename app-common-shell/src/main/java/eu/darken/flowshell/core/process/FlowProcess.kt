package eu.darken.flowshell.core.process

import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FlowProcess(
    sessionLaunch: suspend () -> Process,
    sessionCheck: suspend (Process) -> Boolean = { it.isAlive2 },
    sessionKill: suspend (Process) -> Unit = { it.destroyForcibly() },
) {

    private val processCreator = callbackFlow {
        if (FlowShellDebug.isDebug) log(TAG, VERBOSE) { "PROCESS: Launchin..." }
        val process = sessionLaunch()
        if (FlowShellDebug.isDebug) log(TAG, VERBOSE) { "PROCESS: Launched!" }

        val processExitCode = MutableSharedFlow<Int>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.SUSPEND
        )

        val killRoutine: suspend () -> Unit = {
            try {
                sessionKill(process)
            } catch (e: Exception) {
                log(TAG, ERROR) { "PROCESS: sessionKill threw up: ${e.asLog()}" }
                throw e
            }
        }

        val session = Session(
            process = process,
            exitCode = processExitCode,
            onKill = {
                if (sessionCheck(process)) {
                    if (FlowShellDebug.isDebug) log(TAG) { "PROCESS: Kill session due to kill()..." }
                    killRoutine()
                }
            }
        )
        send(session)

        launch(Dispatchers.IO) {
            if (FlowShellDebug.isDebug) log(TAG, VERBOSE) { "PROCESS: Waiting for process finish" }
            val result = process.waitFor()
            if (FlowShellDebug.isDebug) log(TAG) { "PROCESS: Has finished with CODE $result" }
            this@callbackFlow.cancel("Process ended with $result")
            processExitCode.emit(result)
        }

        awaitClose {
            if (FlowShellDebug.isDebug) log(TAG, VERBOSE) { "PROCESS: Closing..." }
            runBlocking {
                if (sessionCheck(process)) {
                    if (FlowShellDebug.isDebug) log(TAG) { "PROCESS: Kill session due to awaitClose()..." }
                    killRoutine()
                }
            }
            if (FlowShellDebug.isDebug) log(TAG, VERBOSE) { "PROCESS: Closed!" }
        }
    }

    val session: Flow<Session> = processCreator

    data class Session(
        val process: Process,
        val exitCode: Flow<Int>,
        val onKill: suspend () -> Unit,
    ) {
        suspend fun kill() = onKill()
    }

    companion object {
        private const val TAG = "FS:FlowProcess"
    }
}
