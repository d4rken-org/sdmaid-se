package eu.darken.flowshell.core.process


import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.sdmse.common.debug.logging.log
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
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
        var checked = false
        var killed = false
        val flow = FlowProcess(
            sessionLaunch = {
                opened = true
                ProcessBuilder("echo", "hello").start()
            },
            sessionCheck = {
                checked = true
                it.isAlive2
            },
            sessionKill = {
                killed = true
                it.destroyForcibly()
            }
        )

        flow.session.first()

        opened shouldBe true
        checked shouldBe true
        killed shouldBe true
    }

    @Test fun `session waits`() = runTest {
        var started = -1L
        var stopped = -1L
        val flow = FlowProcess(
            sessionLaunch = {
                ProcessBuilder("sleep", "1").start().also {
                    started = System.currentTimeMillis()
                }
            },
            sessionCheck = { true },
            sessionKill = {
                stopped = System.currentTimeMillis()
                log { "Killing process" }
                it.destroyForcibly()
                log { "Process killed" }
            }
        )

        log { "Waiting for exit code" }
        flow.session.flatMapConcat { it.exitCode }.first() shouldBe 0

        (stopped - started) shouldBeGreaterThan 1000
    }

    @Test fun `session can be killed`() = runTest {
        val flow = FlowProcess(
            sessionLaunch = {
                log { "Launching process" }
                ProcessBuilder("sleep", "3").start().also {
                    log { "Process launched" }
                }
            },
        )

        flow.session
            .flatMapConcat {
                it.kill()
                it.exitCode
            }
            .first() shouldBe 137
    }

    @Test fun `session is killed on scope cancel`() = runTest {
        var started = -1L
        var stopped = -1L

        val flow = FlowProcess(
            sessionLaunch = {
                log { "Launching process" }
                ProcessBuilder("sleep", "3").start().also {
                    log { "Process launched" }
                    started = System.currentTimeMillis()
                }
            },
            sessionKill = {
                log { "Killing process" }
                it.destroyForcibly()
                log { "Process killed" }
                stopped = System.currentTimeMillis()
            }
        )

        log { "Waiting for exit code" }
        flow.session.first().exitCode.first() shouldBe 137

        (stopped - started) shouldBeLessThan 3000
    }

    @Test fun `exception on close`() = runTest {
        val flow = FlowProcess(
            sessionLaunch = {
                log { "Launching process" }
                ProcessBuilder("echo", "N+M").start().also {
                    log { "Process launched" }
                }
            },
            sessionKill = {
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
            sessionLaunch = {
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
            sessionLaunch = {
                ProcessBuilder("echo", "<3").start().also {
                    startCount++
                }
            },
        )

        log { "Waiting for exit code (launch #1)" }
        flow.session.flatMapConcat { it.exitCode }.first() shouldBe 0
        startCount shouldBe 1

        log { "Waiting for exit code (launch #2)" }
        flow.session.flatMapConcat { it.exitCode }.first() shouldBe 0
        startCount shouldBe 2
    }

    @Test fun `session is kill and restartable`() = runTest {
        var startCount = 0
        val flow = FlowProcess(
            sessionLaunch = {
                ProcessBuilder("sleep", "1").start().also {
                    startCount++
                }
            },
        )

        log { "Starting and killing (launch #1)" }
        flow.session.first().exitCode.first() shouldBe 137
        startCount shouldBe 1

        log { "Waiting for exit code (launch #2)" }
        flow.session.flatMapConcat { it.exitCode }.first() shouldBe 0
        startCount shouldBe 2
    }
}