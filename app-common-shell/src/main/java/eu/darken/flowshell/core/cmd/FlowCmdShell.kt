package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.FlowShell
import eu.darken.flowshell.core.FlowShellDebug
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

class FlowCmdShell(
    flowShell: FlowShell,
    internal val deathDrainIdleMs: Long = DEATH_DRAIN_IDLE_MS,
) {

    constructor(shell: String = "sh") : this(FlowShell(shell))

    private val sessionProducer = flowShell.session
        .onStart { if (isDebug) log(TAG, VERBOSE) { "Starting session..." } }
        .map { shellSession ->
            if (isDebug) log(TAG, VERBOSE) { "Wrapping to command shell session..." }
            Session(session = shellSession, deathDrainIdleMs = deathDrainIdleMs)
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
        internal val deathDrainIdleMs: Long = DEATH_DRAIN_IDLE_MS,
    ) {
        private val _tag = "${TAG}:${session.session.id}"
        private val scope = CoroutineScope(Job() + Dispatchers.IO)
        private val mutex = Mutex()

        private var cmdCount = 0
        val counter: Int
            get() = cmdCount

        @Volatile private var streamEnded = false

        // The first failure that terminated one of the streams. Kept at session level so that
        // rejections which have no per-command events (the entry guard) can still name the root
        // cause instead of only reporting that the session is gone. Published before streamEnded,
        // so anyone who sees the flag also sees the cause.
        private val firstEndCause = AtomicReference<Throwable?>(null)

        suspend fun isAlive() = session.isAlive()

        // Whether execute() would still accept a command. The process exit code is published by a
        // separate exit-monitor coroutine, so it lags behind the stream EOF that already made
        // execute() reject everything: isAlive() alone can say "alive" for a session that throws on
        // its very next command. Only ever a snapshot — EOF can always land right after the answer
        // is given — but it must not be stale by construction, hence the re-read across the
        // suspending liveness check.
        suspend fun isUsable(): Boolean {
            if (streamEnded) return false
            if (!session.isAlive()) return false
            return !streamEnded
        }

        suspend fun waitFor() = session.waitFor()

        suspend fun cancel() = withContext(Dispatchers.IO) {
            if (isDebug) log(_tag) { "kill()" }
            session.cancel()
            scope.cancel()
        }

        suspend fun close() = withContext(Dispatchers.IO) {
            if (isDebug) log(_tag) { "close()" }
            session.close()
            scope.cancel()
        }

        private sealed interface StreamEvent {
            data class Line(val value: String) : StreamEvent
            data class End(val cause: Throwable?) : StreamEvent
        }

        // Materializes stream termination as an in-band event: SharedFlows don't propagate
        // upstream completion to their subscribers, so without this a harvester could never
        // tell "no output yet" from "there will never be output again".
        private fun Flow<String>.withTerminalEvent(): Flow<StreamEvent> = this
            .map<String, StreamEvent> { StreamEvent.Line(it) }
            .onCompletion { cause ->
                if (cause == null) {
                    streamEnded = true
                    emit(StreamEvent.End(null))
                }
            }
            .catch { cause ->
                firstEndCause.compareAndSet(null, cause)
                streamEnded = true
                emit(StreamEvent.End(cause))
            }

        private val sharedOutput = session.output
            .withTerminalEvent()
            .shareIn(scope, started = SharingStarted.Eagerly, replay = 1)
        private val sharedErrors = session.error
            .withTerminalEvent()
            .shareIn(scope, started = SharingStarted.Eagerly, replay = 1)

        suspend fun execute(cmd: FlowCmd): FlowCmd.Result = withContext(Dispatchers.IO) {
            mutex.withLock {
                if (streamEnded) throw IllegalStateException("Shell session stream has ended", firstEndCause.get())

                cmdCount++
                val id = UUID.randomUUID().toString()
                val idStart = "$id-start"
                val idEnd = "$id-end"
                log(_tag, VERBOSE) { "submit($cmdCount): $cmd" }

                val output = mutableListOf<String>()
                var outputEnd: StreamEvent.End? = null
                val outputReady = CompletableDeferred<Unit>()
                val outputJob = sharedOutput
                    .onSubscription {
                        outputReady.complete(Unit)
                        if (isDebug) log(_tag, VERBOSE) { "Output monitor started ($id)" }
                    }
                    .takeWhile { event ->
                        if (event is StreamEvent.End) {
                            outputEnd = event
                            streamEnded = true
                            false
                        } else {
                            true
                        }
                    }
                    .map { (it as StreamEvent.Line).value }
                    .dropWhile { it != idStart }.drop(1)
                    .onEach {
                        if (isDebug) log(_tag, VERBOSE) { "Adding (output-$id) $it" }
                        output.add(it)
                    }
                    .takeWhile { !it.startsWith(idEnd) }
                    .onCompletion { if (isDebug) log(_tag, VERBOSE) { "Output monitor finished ($id)" } }
                    .launchIn(this + Dispatchers.IO)

                val errors = mutableListOf<String>()
                var errorEnd: StreamEvent.End? = null
                var sawErrorMarker = false
                val errorReady = CompletableDeferred<Unit>()
                val errorJob = sharedErrors
                    .onSubscription {
                        errorReady.complete(Unit)
                        if (isDebug) log(_tag, VERBOSE) { "Error monitor started ($id)" }
                    }
                    .takeWhile { event ->
                        if (event is StreamEvent.End) {
                            errorEnd = event
                            streamEnded = true
                            false
                        } else {
                            true
                        }
                    }
                    .map { (it as StreamEvent.Line).value }
                    .dropWhile { it != idStart }.drop(1)
                    .takeWhile {
                        if (it == idEnd) {
                            sawErrorMarker = true
                            false
                        } else {
                            true
                        }
                    }
                    .onEach {
                        if (isDebug) log(_tag, VERBOSE) { "Adding (errors-$id) $it" }
                        errors.add(it)
                    }
                    .onCompletion { if (isDebug) log(_tag, VERBOSE) { "Error monitor finished ($id)" } }
                    .launchIn(this + Dispatchers.IO)

                // The in-band End events above only arrive while the streams are readable. If the
                // process is killed while its pipes stay open (or a peer keeps a write end alive),
                // no End is ever emitted and the harvesters would wait for markers that can't come.
                // Once the process has exited, drain whatever is still buffered and cut the
                // harvesters loose as soon as they go idle, so joinAll() can return.
                val deathWatcher = launch(this.coroutineContext + Dispatchers.IO) {
                    session.exitCode.filterNotNull().first()
                    if (isDebug) log(_tag, VERBOSE) { "Shell session died, draining harvesters ($id)" }
                    while (true) {
                        val progress = output.size + errors.size
                        delay(deathDrainIdleMs)
                        if (outputJob.isCompleted && errorJob.isCompleted) break
                        if (output.size + errors.size != progress) continue
                        if (isDebug) log(_tag, VERBOSE) { "Drain went idle, cancelling harvesters ($id)" }
                        outputJob.cancel()
                        errorJob.cancel()
                        break
                    }
                }

                listOf(outputReady, errorReady).awaitAll()

                if (outputJob.isCompleted || errorJob.isCompleted || streamEnded) {
                    // The streams are gone before a single byte was written, so this command was
                    // never submitted: no markers can arrive and no replacement process exists to
                    // wait for. Tear the harvesters down and fail instead of waiting for anything.
                    log(_tag, WARN) { "Streams already ended, aborting ($id)" }

                    outputJob.cancel()
                    errorJob.cancel()
                    deathWatcher.cancel()
                    listOf(outputJob, errorJob).joinAll()

                    throw IllegalStateException(
                        "Shell session stream has ended",
                        outputEnd?.cause ?: errorEnd?.cause ?: firstEndCause.get(),
                    )
                }

                if (isDebug) log(_tag, VERBOSE) { "Harvesters are ready, writing commands... ($id)" }

                session.write("echo $idStart", false)
                session.write("echo $idStart >&2", false)
                cmd.instructions.forEach { session.write(it, flush = false) }
                session.write("echo $idEnd $?", false)
                session.write("echo $idEnd >&2", true)

                if (isDebug) log(_tag, VERBOSE) { "Commands are written, waiting... ($id)" }

                listOf(outputJob, errorJob).joinAll()
                deathWatcher.cancel()

                if (isDebug) log(_tag, VERBOSE) { "Determining exitcode ($id)" }

                val endLine = output.lastOrNull()?.takeIf { it.startsWith(idEnd) }
                val exitCode = when {
                    endLine != null && sawErrorMarker -> {
                        output.removeAt(output.lastIndex)
                        endLine.split(" ")
                            .let { it.getOrNull(1)?.toIntOrNull() }
                            ?.let { FlowProcess.ExitCode(it) }
                            ?: throw IllegalArgumentException("Failed to determine exitcode from $endLine")
                    }

                    endLine == null && !sawErrorMarker && cmd.instructions.any { EXEC_REGEX.containsMatchIn(it) } -> {
                        // The caller asked the shell to `exec ...` something that replaces the shell
                        // process — no idEnd marker will ever be echoed on either stream. The sentinel
                        // only applies to such fully markerless results: if exactly one marker showed
                        // up, the shell survived the `exec` (e.g. a redirection-only `exec 2>...`) and
                        // waiting for an exit that never comes would hang, so that case throws below.
                        // RootHostLauncher relies on this call staying blocked until the replacement
                        // dies, so wait for the exit before surfacing a sentinel exit code. A non-null
                        // End cause is deliberately absorbed here: the streams tearing down IS the
                        // expected outcome.
                        if (isDebug) log(_tag, VERBOSE) { "No end marker after exec-replacement ($id)" }
                        session.waitFor()
                        FlowProcess.ExitCode(-1)
                    }

                    else -> throw IllegalStateException(
                        "Command $cmd ended without exit-code marker; shell session likely died externally",
                        outputEnd?.cause ?: errorEnd?.cause,
                    )
                }

                FlowCmd.Result(
                    original = cmd,
                    exitCode = exitCode,
                    output = output,
                    errors = errors
                ).also { log(_tag) { "submit($cmdCount): $cmd -> $it" } }
            }
        }
    }

    companion object {
        private val TAG = "${FlowShellDebug.tag}:FlowCmdShell"

        // Detects a top-level `exec ...` directive (anchored after optional whitespace and
        // env var assignments). Used to recognise commands where the shell intentionally
        // replaces itself with another process — no `echo idEnd` will be emitted in that
        // case, so execute() must surface completion as ExitCode(-1) instead of throwing.
        private val EXEC_REGEX = Regex("""(^|;|&&|\|\|)\s*(\w+=\S+\s+)*exec\s""")

        // How long the post-mortem drain waits for further output before giving up on
        // harvesters whose streams stayed open after the process died.
        private const val DEATH_DRAIN_IDLE_MS = 500L
    }
}
