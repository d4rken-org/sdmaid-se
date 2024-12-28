package eu.darken.flowshell.core


import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.concurrent.CancellationException

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
        shouldThrow<CancellationException> {
            shell.session.flowOn(Dispatchers.IO).collect { session ->
                session.output.onEach { output.add(it) }.launchIn(this)
                session.error.onEach { errors.add(it) }.launchIn(this)
                rows.forEach {
                    session.write("echo test $it")
                    session.write("echo error $it 1>&2")
                    delay(it)
                }
                session.close()
                session.exitCode.first() shouldBe FlowShell.ExitCode.OK
            }
        }
        output shouldBe rows.map { "test $it" }
        errors shouldBe rows.map { "error $it" }
    }

    @Test fun `session waits`() = runTest {
    }

    @Test fun `session can be closed`() = runTest {

    }

    @Test fun `wait for blocks until exit`() = runTest {

    }

    @Test fun `session can be killed`() = runTest {
    }

    @Test fun `session is killed on scope cancel`() = runTest {
    }

    @Test fun `exception on close`() = runTest {
    }

    @Test fun `exception on open`() = runTest {
    }

    @Test fun `session is restartable`() = runTest {
    }

    @Test fun `session is kill and restartable`() = runTest {
    }

    @Test fun `consumer is blocking producer`() = runTest {

    }
}