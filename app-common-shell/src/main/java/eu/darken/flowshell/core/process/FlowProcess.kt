package eu.darken.flowshell.core.process

import eu.darken.flowshell.core.FlowShellDebug
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
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class FlowProcess(
    launch: suspend () -> Process,
    kill: suspend (Process) -> Unit = { if (it.isAlive) it.destroyForcibly() },
) {

    private val processCreator = callbackFlow {
        val shortId = UUID.randomUUID().toString().takeLast(4)
        val _tag = "$TAG:$shortId"
        if (isDebug) log(_tag, VERBOSE) { "Launching..." }
        val process = launch()
        if (isDebug) log(_tag, VERBOSE) { "Launched!" }

        val processExitCode = MutableStateFlow<ExitCode?>(null)

        val killRoutine: suspend () -> Unit = {
            if (isDebug) log(_tag, VERBOSE) { "killRoutine is executing" }
            try {
                kill(process)
            } catch (e: Exception) {
                log(_tag, ERROR) { "sessionKill threw up: ${e.asLog()}" }
                throw e
            }
        }

        val session = Session(
            id = shortId,
            process = process,
            exitCode = processExitCode,
            onKill = {
                if (isDebug) log(_tag) { "Kill session due to kill()..." }
                killRoutine()
            }
        )

        // Send session first
        if (isDebug) log(_tag) { "Emitting session: $session" }
        send(session)

        // Otherwise we could already have closed, if the process is short
        launch(Dispatchers.IO + NonCancellable) {
            if (isDebug) log(_tag, VERBOSE) { "Exit-monitor: Waiting for process finish" }
            val code = process.waitFor().let { ExitCode(it) }
            if (isDebug) log(_tag) { "Exit-monitor: Process finished with $code" }
            processExitCode.value = code
            this@callbackFlow.close()
        }

        if (isDebug) log(_tag, VERBOSE) { "Waiting for flow to close..." }
        awaitClose {
            if (isDebug) log(_tag, VERBOSE) { "awaitClose() passed, flow is closing..." }
            runBlocking {
                killRoutine()
                if (isDebug) log(_tag) { "kill() executed, waiting for process to terminate" }
                process.waitFor()
                if (isDebug) log(_tag) { "Process has terminated" }
            }
            if (isDebug) log(_tag, VERBOSE) { "Flow is closed!" }
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
        internal val id: String,
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
        private val TAG = "${FlowShellDebug.tag}:FlowProcess"
    }
}
