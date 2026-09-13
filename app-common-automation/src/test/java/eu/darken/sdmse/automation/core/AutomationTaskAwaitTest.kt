package eu.darken.sdmse.automation.core

import eu.darken.sdmse.automation.core.errors.AutomationNotRunningException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

/**
 * The task runs on the service's own scope, not on the caller's, so it is modelled here as a
 * [SupervisorJob] scope that can die independently of the awaiting coroutine.
 */
class AutomationTaskAwaitTest : BaseTest() {

    private val tag = "Test"
    private val logId = "submit(1)"

    private fun TestScope.serviceScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test
    fun `the service scope dying while we wait is a failure, not a cancellation`() = runTest2 {
        val serviceScope = serviceScope()
        val task = serviceScope.async { awaitCancellation() }
        runCurrent()

        serviceScope.cancel()
        runCurrent()

        shouldThrow<AutomationNotRunningException> {
            task.awaitAutomationResult(tag = tag, logId = logId)
        }
    }

    @Test
    fun `work the task already reported survives the service scope dying`() = runTest2 {
        val serviceScope = serviceScope()
        val cleared = mutableListOf<String>()
        val task = serviceScope.async {
            cleared.add("eu.thedarken.sdm")
            awaitCancellation()
        }
        runCurrent()

        serviceScope.cancel()
        runCurrent()

        shouldThrow<AutomationNotRunningException> {
            task.awaitAutomationResult(tag = tag, logId = logId)
        }
        cleared shouldBe listOf("eu.thedarken.sdm")
    }

    @Test
    fun `a task cancelled with the user marker stays a cancellation`() = runTest2 {
        val serviceScope = serviceScope()
        val task = serviceScope.async { awaitCancellation() }
        runCurrent()

        task.cancel(UserCancelledAutomationException())
        runCurrent()

        shouldThrow<UserCancelledAutomationException> {
            task.awaitAutomationResult(tag = tag, logId = logId)
        }

        serviceScope.cancel()
    }

    @Test
    fun `a cancelled caller cancels and joins the task before the cancellation escapes`() = runTest2 {
        val serviceScope = serviceScope()
        val task = serviceScope.async { awaitCancellation() }
        runCurrent()

        val escaped = CompletableDeferred<Throwable>()
        var taskDoneWhenThrown: Boolean? = null
        val caller = launch {
            try {
                task.awaitAutomationResult(tag = tag, logId = logId)
            } catch (e: Throwable) {
                taskDoneWhenThrown = task.isCompleted
                escaped.complete(e)
                throw e
            }
        }
        runCurrent()

        caller.cancel()
        caller.join()

        escaped.await().shouldBeInstanceOf<CancellationException>()
        taskDoneWhenThrown shouldBe true

        serviceScope.cancel()
    }
}
