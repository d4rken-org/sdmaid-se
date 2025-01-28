package eu.darken.sdmse.common.shell


import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.sdmse.common.debug.Bugs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Duration.Companion.seconds

class SharedShellTest : BaseTest() {
    @BeforeEach
    fun setup() {
        FlowShellDebug.isDebug = true
        Bugs.apply {
            isDebug = true
            isTrace = true
            isDive = true
        }
    }

    @AfterEach
    fun teardown() {
        FlowShellDebug.isDebug = false
        Bugs.apply {
            isDebug = false
            isTrace = false
            isDive = false
        }
    }

    @Test fun `base operation`() = runTest2(autoCancel = true) {
        val sharedShell = SharedShell("SDMSE", this + Dispatchers.Default)
        sharedShell.useRes { FlowCmd("echo test").execute(it) }.apply {
            isSuccessful shouldBe true
        }
    }

    @Test fun `reuse in the same session`() = runTest2(autoCancel = true, timeout = 600.seconds) {
        val sharedShell = SharedShell("SDMSE", this + Dispatchers.Default)
        sharedShell.useRes {
            (1..1000).forEach { count ->
                FlowCmd("echo test-$count").execute(it).apply {
                    isSuccessful shouldBe true
                }
            }
        }
    }

    @Test fun `reuse while closing the sessions`() = runTest2(autoCancel = true, timeout = 600.seconds) {
        val sharedShell = SharedShell("SDMSE", this + Dispatchers.Default)
        (1..1000).forEach { count ->
            sharedShell.useRes { FlowCmd("echo test-$count").execute(it) }.apply {
                isSuccessful shouldBe true
                output shouldBe listOf("test-$count")
            }
        }
    }

    @Test fun `reuse with cancel`() = runTest2(autoCancel = true, timeout = 600.seconds) {
        val sharedShell = SharedShell("SDMSE", this + Dispatchers.Default)

        shouldThrow<CancellationException> {
            coroutineScope {
                sharedShell.useRes { session ->
                    (1..1000).forEach { count ->
                        FlowCmd("echo test-$count").execute(session).isSuccessful shouldBe true
                    }
                    launch {
                        this@coroutineScope.cancel()
                    }
                }
            }
        }

        (1..100).forEach { count ->
            sharedShell.useRes { FlowCmd("echo test-$count").execute(it) }.isSuccessful shouldBe true
        }
    }
}