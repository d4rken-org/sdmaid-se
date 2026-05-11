package eu.darken.flowshell.core.cmd


import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.flow.replayingShare
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

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

}