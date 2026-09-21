package eu.darken.sdmse.automation.core.errors

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UnusableFailureBudgetTest : BaseTest() {

    private fun timeout() = mockk<AutomationTimeoutException>()

    @Test
    fun `an automation timeout counts`() {
        val budget = UnusableFailureBudget(limit = 1)

        budget.onFailure(timeout())

        budget.isExhausted shouldBe true
    }

    @Test
    fun `an unretryable step abort counts`() {
        val budget = UnusableFailureBudget(limit = 1)

        budget.onFailure(StepAbortException("Unreachable", treatAsSuccess = false))

        budget.isExhausted shouldBe true
    }

    @Test
    fun `a step abort treated as success does not count`() {
        val budget = UnusableFailureBudget(limit = 1)

        budget.onFailure(StepAbortException("Nothing to do", treatAsSuccess = true))

        budget.isExhausted shouldBe false
    }

    @Test
    fun `an ordinary error does not count`() {
        val budget = UnusableFailureBudget(limit = 1)

        budget.onFailure(IllegalStateException("not in package repo"))

        budget.isExhausted shouldBe false
    }

    @Test
    fun `the budget is exhausted at the limit, not before`() {
        val budget = UnusableFailureBudget()

        repeat(AUTOMATION_FAILURE_LIMIT - 1) { budget.onFailure(timeout()) }
        budget.isExhausted shouldBe false

        budget.onFailure(timeout())
        budget.isExhausted shouldBe true
    }

    @Test
    fun `counted failures accumulate, nothing resets them`() {
        val budget = UnusableFailureBudget(limit = 3)

        budget.onFailure(timeout())
        budget.onFailure(IllegalStateException("not in package repo"))
        budget.onFailure(StepAbortException("Nothing to do", treatAsSuccess = true))
        budget.onFailure(timeout())
        budget.isExhausted shouldBe false

        budget.onFailure(StepAbortException("Unreachable"))
        budget.isExhausted shouldBe true
    }
}
