package eu.darken.flowshell.core.process

import eu.darken.flowshell.core.FlowShellDebug.isDebug
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class FlowProcess(
    launch: suspend () -> Process,
    kill: suspend (Process) -> Unit = { if (it.isAlive) it.destroyForcibly() },
) {

    private val processCreator = callbackFlow {
        if (isDebug) log(TAG, VERBOSE) { "Launching..." }
        val process = launch()
        if (isDebug) log(TAG, VERBOSE) { "Launched!" }

        val processExitCode = MutableStateFlow<ExitCode?>(null)

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
            onKill = {
                if (isDebug) log(TAG) { "Kill session due to kill()..." }
                killRoutine()
            }
        )

        // Send session first
        if (isDebug) log(TAG) { "Emitting session: $session" }
        send(session)

        // Otherwise we could already have closed, if the process is short
        launch(Dispatchers.IO + NonCancellable) {
            if (isDebug) log(TAG, VERBOSE) { "Exit-monitor: Waiting for process finish" }
            val code = process.waitFor().let { ExitCode(it) }
            if (isDebug) log(TAG) { "Exit-monitor: Process finished with $code" }
            processExitCode.value = code
            this@callbackFlow.close()
        }

        if (isDebug) log(TAG, VERBOSE) { "Waiting for flow to close..." }
        awaitClose {
            if (isDebug) log(TAG, VERBOSE) { "Flow is closing..." }
            runBlocking {
                killRoutine()

                if (isDebug) log(TAG) { "Waiting for process to be terminate" }
                process.waitFor()
                if (isDebug) log(TAG) { "Process has terminated" }
            }
            if (isDebug) log(TAG, VERBOSE) { "Flow is closed!" }
        }
    }

    val session: Flow<Session> = processCreator
        .onStart { if (isDebug) log(TAG, VERBOSE) { "Starting session..." } }
        .onEach { if (isDebug) log(TAG, VERBOSE) { "Emitting $it" } }
        .onCompletion {
            if (isDebug) {
                if (it == null || it is CancellationException) {
                    log(TAG, VERBOSE) { "Flow is complete. (reason=$it)" }
                } else {
                    log(TAG, WARN) { "Flow is completed unexpectedly: ${it.asLog()}" }
                }
            }
        }

    data class Session(
        private val process: Process,
        val exitCode: Flow<ExitCode?>,
        private val onKill: suspend () -> Unit,
    ) {
        val input = process.outputStream
        val output = process.inputStream
        val errors = process.errorStream

        suspend fun waitFor() = withContext(Dispatchers.IO) {
            exitCode.filterNotNull().first()
        }

        suspend fun isAlive() = exitCode.first() == null

        suspend fun cancel() = withContext(Dispatchers.IO) {
            onKill()
        }
    }

    data class ExitCode(val value: Int) {
        companion object {
            val OK = ExitCode(0)
        }
    }

    companion object {
        private const val TAG = "FS:FlowProcess"
    }
}
