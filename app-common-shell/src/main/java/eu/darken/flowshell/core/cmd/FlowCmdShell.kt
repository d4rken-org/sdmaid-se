package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.FlowShell
import eu.darken.flowshell.core.FlowShellDebug.isDebug
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class FlowCmdShell(
    flowShell: FlowShell
) {

    constructor(shell: String = "sh") : this(FlowShell(shell))

    private val sessionProducer = flowShell.session
        .onStart { if (isDebug) log(TAG, VERBOSE) { "Starting session..." } }
        .map { shellSession ->
            if (isDebug) log(TAG, VERBOSE) { "Wrapping to command shell session..." }
            Session(session = shellSession)
        }
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

    val session: Flow<Session> = sessionProducer

    data class Session(
        private val session: FlowShell.Session,
    ) {
        private val scope = CoroutineScope(Job() + Dispatchers.IO)
        private val mutex = Mutex()

        private var cmdCount = 0
        val counter: Int
            get() = cmdCount

        suspend fun isAlive() = session.isAlive()

        suspend fun waitFor() = session.waitFor()

        suspend fun cancel() = withContext(Dispatchers.IO) {
            if (isDebug) log(TAG) { "kill()" }
            session.cancel()
            scope.cancel()
        }

        suspend fun close() = withContext(Dispatchers.IO) {
            if (isDebug) log(TAG) { "close()" }
            session.close()
            session.waitFor()
            scope.cancel()
        }

        private val sharedOutput = session.output.shareIn(scope, started = SharingStarted.Eagerly)
        private val sharedErrors = session.error.shareIn(scope, started = SharingStarted.Eagerly)

        suspend fun execute(cmd: FlowCmd): FlowCmd.Result = withContext(Dispatchers.IO) {
            mutex.withLock {
                cmdCount++
                val id = UUID.randomUUID().toString()
                val idStart = "$id-start"
                val idEnd = "$id-end"
                log(TAG, VERBOSE) { "submit($cmdCount): $cmd" }

                val output = mutableListOf<String>()
                val outputReady = CompletableDeferred<Unit>()
                val outputJob = sharedOutput
                    .onSubscription {
                        outputReady.complete(Unit)
                        log(TAG, VERBOSE) { "Output monitor started ($id)" }
                    }
                    .dropWhile { it != idStart }
                    .onEach { log(TAG, VERBOSE) { "Output monitor running ($id)" } }
                    .drop(1)
                    .onEach {
                        log(TAG, VERBOSE) { "Adding (output-$id) $it" }
                        output.add(it)
                    }
                    .takeWhile { !it.startsWith(idEnd) }
                    .onCompletion { if (isDebug) log(TAG, VERBOSE) { "Output monitor finished ($id)" } }
                    .launchIn(this + Dispatchers.IO)

                val errors = mutableListOf<String>()
                val errorReady = CompletableDeferred<Unit>()
                val errorJob = sharedErrors
                    .onSubscription {
                        errorReady.complete(Unit)
                        log(TAG, VERBOSE) { "Error monitor started ($id)" }
                    }
                    .dropWhile { it != idStart }
                    .onEach { log(TAG, VERBOSE) { "Error monitor running ($id)" } }
                    .drop(1)
                    .takeWhile { it != idEnd }
                    .onEach {
                        log(TAG, VERBOSE) { "Adding (errors-$id) $it" }
                        errors.add(it)
                    }
                    .onCompletion { if (isDebug) log(TAG, VERBOSE) { "Error monitor finished ($id)" } }
                    .launchIn(this + Dispatchers.IO)

                listOf(outputReady, errorReady).awaitAll()

                if (isDebug) log(TAG, VERBOSE) { "Harvesters are ready, writing commands... ($id)" }

                session.write("echo $idStart", false)
                session.write("echo $idStart >&2", false)
                cmd.instructions.forEach { session.write(it, flush = false) }
                session.write("echo $idEnd $?", false)
                session.write("echo $idEnd >&2", true)

                if (isDebug) log(TAG, VERBOSE) { "Commands are written, waiting... ($id)" }

                listOf(outputJob, errorJob).joinAll()

                if (isDebug) log(TAG, VERBOSE) { "Determining exitcode ($id)" }
                val rawExitCodeRow = output.removeLast()

                val exitCode = rawExitCodeRow
                    .split(" ")
                    .let { it[1].toIntOrNull() }
                    ?.let { FlowProcess.ExitCode(it) }
                    ?: throw IllegalArgumentException("Failed to determine exitcode from $rawExitCodeRow")

                FlowCmd.Result(
                    original = cmd,
                    exitCode = exitCode,
                    output = output,
                    errors = errors
                ).also { log(TAG) { "submit($cmdCount): $cmd -> $it" } }
            }
        }
    }

    companion object {
        private const val TAG = "FS:FlowCmdShell"
    }
}