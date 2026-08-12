package eu.darken.flowshell.core


import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.replayingShare
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.Base64
import java.util.UUID

class FlowShellTest : BaseTest() {
    @BeforeEach
    fun setup() {
        FlowShellDebug.isDebug = true
    }

    @AfterEach
    fun teardown() {
        FlowShellDebug.isDebug = false
    }

    @Test fun `base operation`() = runTest {
        val shell = FlowShell()

        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val rows = (1..10L)

        shell.session.flowOn(Dispatchers.IO).collect { session ->
            val outputJob = session.output.onEach { output.add(it) }.launchIn(this)
            val errorJob = session.error.onEach { errors.add(it) }.launchIn(this)
            rows.forEach {
                session.write("echo test $it")
                session.write("echo error $it 1>&2")
                delay(it)
            }
            session.close()
            session.isAlive() shouldBe false
            session.waitFor() shouldBe FlowProcess.ExitCode.OK
            outputJob.join()
            errorJob.join()
        }

        output shouldBe rows.map { "test $it" }
        errors shouldBe rows.map { "error $it" }
    }

    @Test fun `exitcode behavior`() = runTest2(autoCancel = true) {
        val sharedSession = FlowShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)

        sharedSession.first().apply {
            isAlive() shouldBe true
            exitCode.first() shouldBe null
            close()
            waitFor() shouldBe FlowProcess.ExitCode.OK
            exitCode.first() shouldBe waitFor()
            isAlive() shouldBe false
        }
    }

    @Test fun `session can be closed`() = runTest2(autoCancel = true) {
        val sharedSession = FlowShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)
        sharedSession.first().apply {
            close()
            waitFor() shouldBe FlowProcess.ExitCode.OK
        }
    }

    @Test fun `session can be killed`() = runTest2(autoCancel = true) {
        val sharedSession = FlowShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)
        sharedSession.first().apply {
            cancel()
            waitFor() shouldBe FlowProcess.ExitCode(137)
        }
    }

    @Test fun `slow consumer`() = runTest2(autoCancel = true) {
        val sharedSession = FlowShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)

        val loop = 1000
        val expected = mutableListOf<String>()
        val output = mutableListOf<String>()

        val session = sharedSession.first()

        (1..loop).forEach {
            val data = "$it# ${UUID.randomUUID()}"
            session.write("echo $data")
            expected.add(data)
        }
        session.close()

        session.output.collect { output.add(it) }

        sharedSession.first().waitFor() shouldBe FlowProcess.ExitCode.OK
        output shouldBe expected
        output.size shouldBe loop
    }

    @Test fun `blocking consumer`() = runTest2(autoCancel = true) {
        val sharedSession = FlowShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)

        val session = sharedSession.first()
        val expectedSize = 1048576 * 2
        val outputData = StringBuffer()
        val errorData = StringBuffer()

        session.write("head -c $expectedSize < /dev/urandom | base64")
        session.write("head -c $expectedSize < /dev/urandom | base64 1>&2")
        session.write("echo done")
        session.write("echo done 1>&2")

        val job1 = launch(Dispatchers.IO) {
            session.output.takeWhile { it != "done" }.collect { line ->
                outputData.append(line)
            }
            log { "Job1 finished" }
        }
        val job2 = launch(Dispatchers.IO) {
            session.error.takeWhile { it != "done" }.collect { line ->
                errorData.append(line)
            }
            log { "Job2 finished" }
        }

        listOf(job1, job2).joinAll()

        Base64.getDecoder().apply {
            decode(outputData.toString()).size shouldBe expectedSize
            decode(errorData.toString()).size shouldBe expectedSize
            outputData shouldNotBe errorData
        }
    }

    @Test fun `harvester treats stream teardown as end of stream`() = runTest {
        val session = fakeSession(TornDownInputStream("line1\nline2\n"))

        session.output.toList() shouldBe listOf("line1", "line2")
    }

    @Test fun `killing session while output is collected does not throw`() = runTest2(autoCancel = true) {
        val sharedSession = FlowShell().session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)

        val session = sharedSession.first()
        val lineArrived = CompletableDeferred<Unit>()

        val outputJob = session.output
            .onEach { if (it == "marker") lineArrived.complete(Unit) }
            .launchIn(this + Dispatchers.IO)
        val errorJob = session.error.launchIn(this + Dispatchers.IO)

        session.write("echo marker")
        lineArrived.await()

        session.cancel()

        withContext(Dispatchers.IO) {
            withTimeout(10_000) { listOf(outputJob, errorJob).joinAll() }
        }
    }

    @Test fun `collector exceptions propagate through the harvester`() = runTest {
        val session = fakeSession(EndlessInputStream("line"))

        shouldThrow<IllegalStateException> {
            session.output.collect { throw IllegalStateException("collector boom") }
        }.message shouldBe "collector boom"
    }

    private fun fakeSession(output: InputStream): FlowShell.Session {
        val process = mockk<Process>().apply {
            every { outputStream } returns ByteArrayOutputStream()
            every { inputStream } returns output
            every { errorStream } returns ByteArrayInputStream(ByteArray(0))
        }
        return FlowShell.Session(
            session = FlowProcess.Session(
                id = "test",
                process = process,
                exitCode = MutableStateFlow<FlowProcess.ExitCode?>(null),
                onKill = {},
            ),
        )
    }

    private class TornDownInputStream(payload: String) : InputStream() {
        private val data = payload.toByteArray()
        private var pos = 0

        override fun read(): Int {
            if (pos == data.size) throw InterruptedIOException("read interrupted by close() on another thread")
            return data[pos++].toInt() and 0xFF
        }

        override fun close() {
            throw IOException("close() failed")
        }
    }

    private class EndlessInputStream(line: String) : InputStream() {
        private val data = (line + "\n").toByteArray()
        private var pos = 0

        override fun read(): Int {
            val byte = data[pos]
            pos = (pos + 1) % data.size
            return byte.toInt() and 0xFF
        }
    }
}
