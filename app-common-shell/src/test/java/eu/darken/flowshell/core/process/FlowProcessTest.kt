package eu.darken.flowshell.core.process


import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.replayingShare
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException

class FlowProcessTest : BaseTest() {
    @BeforeEach
    fun setup() {
        FlowShellDebug.isDebug = true
    }

    @AfterEach
    fun teardown() {
        FlowShellDebug.isDebug = false
    }

    @Test fun `base opening`() = runTest {
        var opened = false
        var killed = false
        val flow = FlowProcess(
            launch = {
                opened = true
                ProcessBuilder("sh", "-c", "echo 'Error' 1>&2; echo 'Input'; sleep 1").start()
            },
            kill = {
                killed = true
                it.destroyForcibly()
            }
        )

        flow.session.first()

        opened shouldBe true
        killed shouldBe true
    }

    @Test fun `session waits`() = runTest {
        var started = -1L
        var stopped = -1L
        val flow = FlowProcess(
            launch = {
                ProcessBuilder("sleep", "1").start().also {
                    started = System.currentTimeMillis()
                }
            },
            kill = {
                stopped = System.currentTimeMillis()
                log { "Killing process" }
                it.destroyForcibly()
                log { "Process killed" }
            }
        )

        log { "Waiting for exit code" }
        flow.session.collect {
            it.waitFor() shouldBe FlowProcess.ExitCode.OK
        }

        (stopped - started) shouldBeGreaterThanOrEqual 1000
    }

    @Test fun `session stays open`() = runTest {
        val flow = FlowProcess(
            launch = {
                ProcessBuilder("sh").start()
            },
        )
        var session: FlowProcess.Session? = null
        flow.session.onEach { session = it }.launchIn(this)
        delay(100)
        session shouldNotBe null

        val writer = session!!.input.buffered().bufferedWriter()

        (1..100).forEach {
            writer.write("echo hi$it\n")
            writer.flush()
        }

        session!!.isAlive() shouldBe true

        writer.write("exit\n")
        writer.flush()

        log { "Waiting for exit code" }
        session!!.waitFor() shouldBe FlowProcess.ExitCode.OK
    }

    @Test fun `wait for blocks until exit`() = runTest2(autoCancel = true) {
        val sharedSession = FlowProcess(
            launch = { ProcessBuilder("sleep", "1").start() },
        ).session.replayingShare(this)
        sharedSession.launchIn(this + Dispatchers.IO)

        sharedSession.first().apply {
            val start = System.currentTimeMillis()
            waitFor() shouldBe FlowProcess.ExitCode.OK
            val stop = System.currentTimeMillis()
            stop - start shouldBeGreaterThan 900L
        }
    }

    @Test fun `session can be killed`() = runTest {
        var start = 0L
        val flow = FlowProcess(
            launch = {
                ProcessBuilder("sleep", "3").start().also {
                    start = System.currentTimeMillis()
                }
            },
        )

        flow.session.collect {
            it.cancel()
            it.waitFor() shouldBe FlowProcess.ExitCode(137)
        }
        System.currentTimeMillis() - start shouldBeLessThan 2000
    }

    @Test fun `session is killed on scope cancel`() = runTest {
        var started = -1L
        var stopped = -1L

        val flow = FlowProcess(
            launch = {
                log { "Launching process" }
                ProcessBuilder("sleep", "3").start().also {
                    log { "Process launched" }
                    started = System.currentTimeMillis()
                }
            },
            kill = {
                log { "Killing process" }
                it.destroyForcibly()
                log { "Process killed" }
                stopped = System.currentTimeMillis()
            }
        )

        log { "Waiting for exit code" }
        flow.session.first().exitCode.filterNotNull().first() shouldBe FlowProcess.ExitCode(137)

        (stopped - started) shouldBeLessThan 3000
    }

    @Test fun `exception on close`() = runTest {
        val flow = FlowProcess(
            launch = {
                log { "Launching process" }
                ProcessBuilder("sleep", "1").start().also {
                    log { "Process launched" }
                }
            },
            kill = {
                log { "Killing process" }
                throw IOException("test")
            }
        )

        log { "Waiting for throw" }
        shouldThrow<IOException> {
            flow.session.first()
        }
        log { "We threw :)" }
    }

    @Test fun `exception on open`() = runTest {
        val flow = FlowProcess(
            launch = {
                throw IOException("test")
            },
        )

        log { "Waiting for throw" }
        shouldThrow<IOException> {
            flow.session.first()
        }
    }

    @Test fun `session is restartable`() = runTest {
        var startCount = 0
        val flow = FlowProcess(
            launch = {
                ProcessBuilder("echo", "<3").start().also {
                    startCount++
                }
            },
        )

        log { "Waiting for exit code (launch #1)" }
        flow.session.collect {
            it.waitFor() shouldBe FlowProcess.ExitCode.OK
        }
        startCount shouldBe 1

        log { "Waiting for exit code (launch #2)" }
        flow.session.collect {
            it.waitFor() shouldBe FlowProcess.ExitCode.OK
        }
        startCount shouldBe 2
    }

    @Test fun `session is kill and restartable`() = runTest {
        var startCount = 0
        val flow = FlowProcess(
            launch = {
                ProcessBuilder("sleep", "1").start().also {
                    startCount++
                }
            },
        )

        // Immediately ends the scope after the emission
        log { "Starting and killing (launch #1)" }
        flow.session.first().exitCode.first() shouldNotBe FlowProcess.ExitCode.OK
        startCount shouldBe 1

        log { "Waiting for exit code (launch #2)" }
        flow.session.collect {
            it.waitFor() shouldBe FlowProcess.ExitCode.OK
        }
        startCount shouldBe 2
    }

    @Test fun `session is killed via pid`() = runTest {
        var opened = false
        var killed = false
        val flow = FlowProcess(
            launch = {
                opened = true
                ProcessBuilder("sh").start()
            },
            kill = {
                it.killViaPid()
                killed = true
            }
        )

        flow.session.first().apply {
            waitFor() shouldBe FlowProcess.ExitCode(137)
        }

        opened shouldBe true
        killed shouldBe true
    }
}