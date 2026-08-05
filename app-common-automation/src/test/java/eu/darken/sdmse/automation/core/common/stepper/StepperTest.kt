package eu.darken.sdmse.automation.core.common.stepper

import eu.darken.sdmse.automation.core.ScreenState
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.ca.toCaString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

/**
 * [StepAbortException] used to be swallowed unconditionally, which made a step that could not be
 * completed indistinguishable from one that was deliberately skipped. Callers like the AppCleaner
 * clear-cache module then recorded a failed run as a success.
 */
class StepperTest : BaseTest() {

    // Hot flow that stays open like the production callbackFlow, so the screen guard is only
    // torn down by Stepper's own cleanup and not by the source completing on its own.
    private val screenState = mockk<ScreenState>().apply {
        every { state } returns MutableStateFlow(ScreenState.State(isScreenOn = true, isUnlocked = true))
    }
    private val hostContext = mockk<AutomationExplorer.Context>(relaxed = true)
    private val windowRoot = mockk<ACSNodeInfo>(relaxed = true)
    private var nodeActions = 0

    private fun createStep(abort: StepAbortException) = AutomationStep(
        source = "test",
        descriptionInternal = "Aborting step",
        label = "Test".toCaString(),
        windowCheck = { windowRoot },
        nodeAction = {
            nodeActions++
            throw abort
        },
    )

    @Test
    fun `a skip continues the plan`() = runTest2 {
        val step = createStep(StepAbortException("Skipped, handled elsewhere", treatAsSuccess = true))

        Stepper(screenState).process(hostContext, step)

        nodeActions shouldBe 1
    }

    @Test
    fun `a failure propagates instead of being reported as success`() = runTest2 {
        val abort = StepAbortException("Button not reachable")
        abort.treatAsSuccess shouldBe false

        val thrown = shouldThrow<StepAbortException> {
            Stepper(screenState).process(hostContext, createStep(abort))
        }

        thrown shouldBe abort
        nodeActions shouldBe 1
    }
}
