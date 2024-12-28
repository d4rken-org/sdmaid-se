package eu.darken.flowshell.core.process

import eu.darken.flowshell.core.FlowShellDebug.isDebug
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
    launch: suspend () -> Process,
    check: suspend (Process) -> Boolean = { it.isAlive },
    kill: suspend (Process) -> Unit = { it.destroyForcibly() },
) {

    private val processCreator = callbackFlow {
        if (isDebug) log(TAG, VERBOSE) { "Launching..." }
        val process = launch()
        if (isDebug) log(TAG, VERBOSE) { "Launched!" }

        val processExitCode = MutableSharedFlow<Int>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.SUSPEND
        )

        val killRoutine: suspend () -> Unit = {
            try {
                kill(process)
            } catch (e: Exception) {
                log(TAG, ERROR) { "sessionKill threw up: ${e.asLog()}" }
                throw e
            }
        }

        val session = Session(
            process = process,
            exitCode = processExitCode,
            onCheck = check,
            onKill = {
                if (check(process)) {
                    if (isDebug) log(TAG) { "Kill session due to kill()..." }
                    killRoutine()
                }
            }
        )
        if (isDebug) log(TAG) { "Emitting session: $session" }
        send(session)

        launch(Dispatchers.IO) {
            if (isDebug) log(TAG, VERBOSE) { "Waiting for process finish" }
            val result = process.waitFor()
            if (isDebug) log(TAG) { "Has finished with CODE $result" }
            this@callbackFlow.cancel("Process ended with $result")
            processExitCode.emit(result)
        }

        awaitClose {
            if (isDebug) log(TAG, VERBOSE) { "Flow is closing..." }
            runBlocking {
                if (check(process)) killRoutine()

                if (isDebug) log(TAG) { "Waiting for process to terminate" }
                process.waitFor()
                if (isDebug) log(TAG) { "Process has terminated" }
            }
            if (isDebug) log(TAG, VERBOSE) { "Flow is closed!" }
        }
    }

    val session: Flow<Session> = processCreator

    data class Session(
        private val process: Process,
        val exitCode: Flow<Int>,
        private val onKill: suspend () -> Unit,
        private val onCheck: suspend (Process) -> Boolean,
    ) {
        val input = process.outputStream
        val output = process.inputStream
        val errors = process.errorStream

        suspend fun isAlive() = onCheck(process)

        suspend fun kill() = onKill()
    }

    companion object {
        private const val TAG = "FS:FlowProcess"
    }
}
