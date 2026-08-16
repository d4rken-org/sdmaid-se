package eu.darken.flowshell.core.cmd


import eu.darken.flowshell.core.FlowShell
import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.flow.replayingShare
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class FlowCmdShellTest : BaseTest() {
    @BeforeEach
    fun setup() {
        FlowShellDebug.isDebug = true
    }

    @AfterEach
    fun teardown() {
        FlowShellDebug.isDebug = false
    }

    @Test fun `base operation`() = runTest2(autoCancel = true) {
        val sharedSession = FlowCmdShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)
        val session = sharedSession.first()
        session.isAlive() shouldBe true

        val cmd = FlowCmd(
            "echo output test",
            "echo error test >&2",
        )
        session.execute(cmd).apply {
            original shouldBe cmd
            exitCode shouldBe FlowProcess.ExitCode.OK
            output shouldBe listOf("output test")
            errors shouldBe listOf("error test")
        }

        session.close()
        session.isAlive() shouldBe false
    }

    @Test fun `quick execute`() = runTest {
        FlowCmd("echo 123").execute().apply {
            output shouldBe listOf("123")
            exitCode shouldBe FlowProcess.ExitCode.OK
        }
    }

    @Test fun `closing session aborts command`() = runTest2(autoCancel = true) {
        val sharedSession = FlowCmdShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)
        val session = sharedSession.first()

        launch {
            shouldThrow<Exception> {
                FlowCmd("sleep 3").execute(session)
            }
        }

        session.close()
    }

    @Test fun `killing session aborts command`() = runTest2(autoCancel = true) {
        val sharedSession = FlowCmdShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)
        val session = sharedSession.first()

        launch {
            shouldThrow<Exception> {
                FlowCmd("sleep 3").execute(session)
            }
        }

        session.cancel()
    }

    @Test fun `queued commands`() = runTest2(autoCancel = true) {
        FlowCmdShell().session.collect { session ->
            session.counter shouldBe 0
            (1..1000).forEach {
                FlowCmd(
                    "echo output$it",
                    "echo error$it >&2",
                ).execute(session).apply {
                    exitCode shouldBe FlowProcess.ExitCode.OK
                    output shouldBe listOf("output$it")
                    errors shouldBe listOf("error$it")
                }
                session.counter shouldBe it
            }
            session.counter shouldBe 1000
            session.close()
        }
    }

    @Test fun `race command commands`() = runTest2(autoCancel = true) {
        FlowCmdShell().session.collect { session ->
            session.counter shouldBe 0
            (1..1000)
                .map {
                    launch(Dispatchers.IO) {
                        delay(5)
                        FlowCmd(
                            "echo output$it",
                            "echo error$it >&2",
                        ).execute(session).apply {
                            exitCode shouldBe FlowProcess.ExitCode.OK
                            output shouldBe listOf("output$it")
                            errors shouldBe listOf("error$it")
                        }
                    }
                }
                .toList()
                .joinAll()
            session.counter shouldBe 1000
            session.close()
        }
    }

    @Test fun `commands can be timeoutted`(): Unit = runBlocking {
        val start = System.currentTimeMillis()

        shouldThrow<TimeoutCancellationException> {
            withTimeout(1000) {
                FlowCmd("sleep 3", "echo done").execute().apply {
                    exitCode shouldBe FlowProcess.ExitCode.OK
                    output shouldBe listOf("done")
                }
            }
        }
        (System.currentTimeMillis() - start) shouldBeGreaterThanOrEqual 500
        (System.currentTimeMillis() - start) shouldBeLessThan 3000
    }

    @Test fun `open session extension`() = runTest2(autoCancel = true) {
        val (session, job) = FlowCmdShell().openSession(this)

        FlowCmd("echo done").execute(session).apply {
            exitCode shouldBe FlowProcess.ExitCode.OK
            output shouldBe listOf("done")
        }
    }

    @Test fun `cancellation behavior`() = runTest2(autoCancel = true) {
        val (session, job) = FlowCmdShell().openSession(this)

        shouldThrow<TimeoutCancellationException> {
            withTimeout(500) {
                FlowCmd("sleep 3", "echo nope").execute(session).apply {
                    exitCode shouldBe FlowProcess.ExitCode.OK
                }
            }
        }

        FlowCmd("echo done").execute(session).apply {
            exitCode shouldBe FlowProcess.ExitCode.OK
            output shouldBe listOf("done")
        }
    }

    @Test fun `direct execution behavior`() = runTest {
        val start = System.currentTimeMillis()
        FlowCmd("sleep 1", "echo done").execute().apply {
            exitCode shouldBe FlowProcess.ExitCode.OK
            output shouldBe listOf("done")
        }
        (System.currentTimeMillis() - start) shouldBeGreaterThanOrEqual 1000
    }

    @Test fun `exec command replacing shell blocks until replacement exits and does not throw`(): Unit = runBlocking {
        // RootHostLauncher writes an `exec /proc/<pid>/exe ...` line to swap the shell with
        // the root host process. The final `echo idEnd >&2` flush can race against the pipe
        // being torn down, producing IOException("Stream closed"). execute() must absorb
        // that race so the launcher stays blocked until the replacement process exits, and
        // must not propagate the IOException — otherwise the binder connection callback
        // never reaches the SharedResource consumer.
        val start = System.currentTimeMillis()
        val result = FlowCmd("exec sleep 1").execute()
        val elapsed = System.currentTimeMillis() - start

        // joinAll must have blocked until sleep exited (~1s) instead of returning early.
        elapsed shouldBeGreaterThanOrEqual 900
        // No end marker was emitted because sleep replaced the shell; we expose -1 instead
        // of throwing so callers like RootHostLauncher can observe normal completion.
        result.exitCode shouldBe FlowProcess.ExitCode(-1)
    }

    @Test fun `execute fails fast when streams die while process lives`(): Unit = runBlocking {
        LoopbackShell(closeStdoutAtStart = true, closeStderrAtStart = true).use { shell ->
            val session = shell.cmdSession()

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo hi")) }
            }

            shell.exitCode.value shouldBe null
        }
    }

    @Test fun `a stream-dead session is unusable while its exit code still reports alive`(): Unit = runBlocking {
        LoopbackShell(closeStdoutAtStart = true, closeStderrAtStart = true).use { shell ->
            val session = shell.cmdSession()

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo hi")) }
            }

            // The exit code is published by a separate monitor, so it can still say "running" long
            // after the streams ended. Anything deciding whether to hand this session to another
            // caller (SharedShell's reuse check) has to ask isUsable(), not isAlive().
            session.isAlive() shouldBe true
            session.isUsable() shouldBe false
        }
    }

    @Test fun `second execute after stream death is rejected before writing`(): Unit = runBlocking {
        LoopbackShell(closeStdoutAtStart = true, closeStderrAtStart = true).use { shell ->
            val session = shell.cmdSession()

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo first-command")) }
            }

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo second-command")) }
            }

            // Give the interpreter a chance to catch up on anything that was still in flight.
            delay(100)
            shell.consumedLines().none { it.contains("second-command") } shouldBe true
        }
    }

    @Test fun `exec on a dead-stream session fails instead of waiting`(): Unit = runBlocking {
        LoopbackShell(closeStdoutAtStart = true, closeStderrAtStart = true).use { shell ->
            val session = shell.cmdSession()

            // Nothing was submitted, so there is no replacement process whose exit could be awaited.
            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("exec sleep 999")) }
            }

            shell.exitCode.value shouldBe null
        }
    }

    @Test fun `execute after single stream death is rejected deterministically`(): Unit = runBlocking {
        LoopbackShell(closeStderrAtStart = true).use { shell ->
            val session = shell.cmdSession()

            // The End event is published by the eagerly started sharing coroutine, so the rejection
            // does not depend on a command's harvester having observed it first.
            delay(300)

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo probe")) }
            }

            shell.consumedLines().none { it.contains("probe") } shouldBe true
        }
    }

    @Test fun `redirection-only exec fails instead of waiting on the live shell`(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val (session, _) = FlowCmdShell().openSession(scope)

        // `exec 2>/dev/null` only rebinds stderr, it does not replace the shell: stdout still
        // delivers its end marker while the stderr marker is redirected away. Waiting for an exit
        // that never comes would hang here while holding the session mutex.
        withTimeout(5_000) {
            shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("exec 2>/dev/null")) }
        }

        session.cancel()
        scope.cancel()
    }

    @Test fun `stderr dying mid-command does not fake success`(): Unit = runBlocking {
        LoopbackShell(closeStderrBeforeEndMarker = true).use { shell ->
            val session = shell.cmdSession()

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> {
                    session.execute(FlowCmd("echo out", "echo err >&2"))
                }
            }
        }
    }

    @Test fun `buffered markers win over stream end`(): Unit = runBlocking {
        LoopbackShell(exitCodeValue = 7, closePipesAfterProtocol = true).use { shell ->
            val session = shell.cmdSession()

            val result = withTimeout(5_000) {
                session.execute(FlowCmd("echo one", "echo two", "echo err >&2"))
            }

            result.output shouldBe listOf("one", "two")
            result.errors shouldBe listOf("err")
            result.exitCode shouldBe FlowProcess.ExitCode(7)
        }
    }

    @Test fun `process death before drain does not truncate buffered output`(): Unit = runBlocking {
        val lines = 120_000
        // The payload has to outgrow the fixture's pipe buffer, otherwise the interpreter never
        // blocks and the drain watcher's progress-reset branch isn't exercised.
        (1..lines).sumOf { "bulk-$it\n".length } shouldBeGreaterThan (1 shl 20)
        LoopbackShell(
            exitCodeValue = 3,
            dieAfterProtocol = true,
            closePipesAfterProtocol = true,
        ).use { shell ->
            val session = shell.cmdSession()

            val result = withTimeout(60_000) { session.execute(FlowCmd("bulk $lines")) }

            result.output.size shouldBe lines
            result.output.first() shouldBe "bulk-1"
            result.output.last() shouldBe "bulk-$lines"
            result.exitCode shouldBe FlowProcess.ExitCode(3)
        }
    }

    @Test fun `death with silent open pipes cancels after the idle window`(): Unit = runBlocking {
        LoopbackShell(mute = true, dieAfterProtocol = true).use { shell ->
            val session = shell.cmdSession(deathDrainIdleMs = 100)

            withTimeout(5_000) {
                shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo hi")) }
            }
        }
    }

    @Test fun `submitted exec with dead streams waits for process exit`(): Unit = runBlocking {
        LoopbackShell(closePipesOnExecLine = true).use { shell ->
            val session = shell.cmdSession()

            // The command reached the shell before the streams died, so no marker can arrive but
            // the replacement process is real and execute() must stay blocked until it exits.
            val execution = async(Dispatchers.IO) { session.execute(FlowCmd("exec sleep 999")) }

            withTimeoutOrNull(500) { execution.await() } shouldBe null

            shell.exitCode.value = FlowProcess.ExitCode(0)

            withTimeout(5_000) { execution.await() }.exitCode shouldBe FlowProcess.ExitCode(-1)
        }
    }

    @Test fun `upstream failure surfaces as the protocol exception cause`(): Unit = runBlocking {
        val boom = StdoutBoom(marker = 42L)
        val session = fakeCmdSession(
            output = ExplodingInputStream("line1\nline2\n", boom),
            errors = ByteArrayInputStream(ByteArray(0)),
        )

        // Let both sharing coroutines drain their streams first: stderr ends without a cause,
        // stdout ends with the boom, so the rejection has a deterministic root cause to name.
        delay(200)

        val failure = withTimeout(5_000) {
            shouldThrowExactly<IllegalStateException> { session.execute(FlowCmd("echo hi")) }
        }

        generateSequence<Throwable>(failure) { it.cause }.any { it === boom } shouldBe true

        session.cancel()
    }

    private fun fakeCmdSession(
        output: InputStream,
        errors: InputStream,
        exitCode: MutableStateFlow<FlowProcess.ExitCode?> = MutableStateFlow(null),
    ): FlowCmdShell.Session {
        val process = mockk<Process>().apply {
            every { outputStream } returns ByteArrayOutputStream()
            every { inputStream } returns output
            every { errorStream } returns errors
        }
        return FlowCmdShell.Session(
            session = FlowShell.Session(
                session = FlowProcess.Session(
                    id = "test",
                    process = process,
                    exitCode = exitCode,
                    onKill = {},
                ),
            ),
        )
    }

    // Deliberately not copyable by coroutine stacktrace recovery (no (String)/(Throwable)/()
    // constructor), so the instance that reaches the exception cause is the one thrown here.
    private class StdoutBoom(marker: Long) : RuntimeException("stdout stream exploded #$marker")

    private class ExplodingInputStream(
        payload: String,
        private val boom: RuntimeException,
    ) : InputStream() {
        private val data = payload.toByteArray()
        private var pos = 0

        override fun read(): Int {
            if (pos == data.size) throw boom
            return data[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos == data.size) throw boom
            val chunk = minOf(len, data.size - pos)
            System.arraycopy(data, pos, b, off, chunk)
            pos += chunk
            return chunk
        }
    }

    /**
     * A loopback shell: an interpreter thread consumes what execute() writes to stdin and echoes
     * back on the stdout/stderr pipes, so the marker protocol can be broken in specific ways.
     */
    private class LoopbackShell(
        private val exitCodeValue: Int = 0,
        private val mute: Boolean = false,
        private val closeStdoutAtStart: Boolean = false,
        private val closeStderrAtStart: Boolean = false,
        private val closeStderrBeforeEndMarker: Boolean = false,
        private val dieAfterProtocol: Boolean = false,
        private val closePipesAfterProtocol: Boolean = false,
        private val closePipesOnExecLine: Boolean = false,
    ) : AutoCloseable {

        val exitCode = MutableStateFlow<FlowProcess.ExitCode?>(null)

        private val consumed = CopyOnWriteArrayList<String>()
        private val sessions = mutableListOf<FlowCmdShell.Session>()

        private val stdinSink = PipedOutputStream()
        private val stdinSource = PipedInputStream(stdinSink, PIPE_BUFFER)
        private val stdoutSource = PipedInputStream(PIPE_BUFFER)
        private val stdoutSink = PipedOutputStream(stdoutSource)
        private val stderrSource = PipedInputStream(PIPE_BUFFER)
        private val stderrSink = PipedOutputStream(stderrSource)

        @Volatile private var closing = false
        @Volatile private var failure: Throwable? = null
        @Volatile private var silenced = false

        private val interpreter = Thread {
            try {
                stdinSource.bufferedReader().forEachLine { handle(it) }
            } catch (e: Throwable) {
                if (!closing) failure = e
            }
        }.apply {
            name = "loopback-shell"
            isDaemon = true
        }

        init {
            if (closeStdoutAtStart) closeQuietly(stdoutSink)
            if (closeStderrAtStart) closeQuietly(stderrSink)
            interpreter.start()
        }

        fun consumedLines(): List<String> = consumed.toList()

        fun cmdSession(deathDrainIdleMs: Long? = null): FlowCmdShell.Session {
            val process = mockk<Process>().apply {
                every { outputStream } returns stdinSink
                every { inputStream } returns stdoutSource
                every { errorStream } returns stderrSource
            }
            val shellSession = FlowShell.Session(
                session = FlowProcess.Session(
                    id = "loopback",
                    process = process,
                    exitCode = exitCode,
                    onKill = {},
                ),
            )
            val session = when (deathDrainIdleMs) {
                null -> FlowCmdShell.Session(session = shellSession)
                else -> FlowCmdShell.Session(session = shellSession, deathDrainIdleMs = deathDrainIdleMs)
            }
            return session.also { sessions.add(it) }
        }

        private fun handle(line: String) {
            consumed.add(line)
            val isProtocolEnd = END_MARKER_ECHO.matches(line)

            if (isProtocolEnd && closeStderrBeforeEndMarker) closeQuietly(stderrSink)

            if (!mute && !silenced) interpret(line)

            if (closePipesOnExecLine && line.contains("exec ")) {
                // The exec'd process took over: both stream ends die and nothing is echoed back,
                // but the shell was already handed the command.
                closeQuietly(stdoutSink)
                closeQuietly(stderrSink)
                silenced = true
            }

            if (isProtocolEnd) {
                if (dieAfterProtocol) exitCode.value = FlowProcess.ExitCode(exitCodeValue)
                if (closePipesAfterProtocol) {
                    closeQuietly(stdoutSink)
                    closeQuietly(stderrSink)
                }
            }
        }

        private fun interpret(line: String) {
            when {
                line.startsWith("bulk ") -> {
                    val count = line.removePrefix("bulk ").trim().toInt()
                    (1..count).forEach { writeTo(stdoutSink, "bulk-$it") }
                }

                line.startsWith("echo ") && line.endsWith(" >&2") -> {
                    writeTo(stderrSink, line.removePrefix("echo ").removeSuffix(" >&2").resolveExitCode())
                }

                line.startsWith("echo ") -> writeTo(stdoutSink, line.removePrefix("echo ").resolveExitCode())
            }
        }

        private fun String.resolveExitCode() = replace("\$?", exitCodeValue.toString())

        private fun writeTo(sink: PipedOutputStream, line: String) {
            try {
                sink.write((line + "\n").toByteArray())
                sink.flush()
            } catch (e: IOException) {
                // The script closed this pipe — stdin must keep being consumed regardless,
                // otherwise the writer would break before the marker accounting runs.
            }
        }

        private fun closeQuietly(target: Closeable) {
            try {
                target.close()
            } catch (e: IOException) {
                // Already closed or never connected
            }
        }

        override fun close() {
            closing = true
            closeQuietly(stdinSink)
            interpreter.join(2_000)
            listOf(stdoutSink, stderrSink, stdinSource, stdoutSource, stderrSource).forEach { closeQuietly(it) }
            interpreter.join(2_000)
            runBlocking { sessions.forEach { it.cancel() } }
            failure?.let { throw it }
        }

        companion object {
            private const val PIPE_BUFFER = 1 shl 20
            private val END_MARKER_ECHO = Regex("""^echo \S+-end >&2$""")
        }
    }
}
