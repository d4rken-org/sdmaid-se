package eu.darken.sdmse.automation.core.specs

import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.errors.AutomationTimeoutException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Duration

/**
 * A step that aborts without [StepAbortException.treatAsSuccess] has declared itself unretryable,
 * but the explorer used to catch it in its generic retry branch and replay the whole plan until
 * [AutomationSpec.Explorer.executionTimeout] expired. That re-ran the same failing step for 30s per
 * target, so a decided failure looked like a hang: the AppCleaner clear-cache path spent 4.5 minutes
 * relaunching Settings for nine apps before its give-up heuristic tripped.
 */
class AutomationExplorerTest : BaseTest() {

    private val host = mockk<AutomationHost>(relaxed = true)

    private fun spec(
        retryCount: Int = 3,
        timeout: Duration = Duration.ofSeconds(30),
        plan: suspend (AutomationExplorer.Context) -> Unit,
    ) = object : AutomationSpec.Explorer {
        override val tag: String = "test"
        override val executionTimeout: Duration = timeout
        override val executionRetryCount: Int = retryCount
        override suspend fun createPlan(): suspend (AutomationExplorer.Context) -> Unit = plan
    }

    @Test
    fun `an unretryable step abort is replayed only within the retry budget`() = runTest2 {
        var attempts = 0
        val abort = StepAbortException("Button not reachable")
        abort.treatAsSuccess shouldBe false

        val thrown = shouldThrow<StepAbortException> {
            AutomationExplorer(host).process(
                spec(retryCount = 3) {
                    attempts++
                    throw abort
                }
            )
        }

        thrown shouldBe abort
        // Initial attempt plus executionRetryCount replays, then it propagates instead of
        // spinning until executionTimeout.
        attempts shouldBe 4
    }

    @Test
    fun `the retry budget is shared by the whole plan, not refreshed per replay`() = runTest2 {
        var attempts = 0

        shouldThrow<StepAbortException> {
            AutomationExplorer(host).process(
                spec(retryCount = 0) {
                    attempts++
                    throw StepAbortException("Button not reachable")
                }
            )
        }

        attempts shouldBe 1
    }

    @Test
    fun `a step abort marked as success ends the plan without replaying`() = runTest2 {
        var attempts = 0

        AutomationExplorer(host).process(
            spec {
                attempts++
                throw StepAbortException("Handled elsewhere", treatAsSuccess = true)
            }
        )

        attempts shouldBe 1
    }

    @Test
    fun `other failures keep retrying until the execution timeout`() = runTest2 {
        var attempts = 0

        shouldThrow<AutomationTimeoutException> {
            AutomationExplorer(host).process(
                spec(retryCount = 3, timeout = Duration.ofSeconds(3)) {
                    attempts++
                    throw IllegalStateException("Transient failure")
                }
            )
        }

        // The abort budget must not leak into the generic retry path.
        attempts shouldBeGreaterThan 4
    }

    @Test
    fun `a plan that succeeds runs exactly once`() = runTest2 {
        var attempts = 0

        AutomationExplorer(host).process(spec { attempts++ })

        attempts shouldBe 1
    }
}
