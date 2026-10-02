package eu.darken.sdmse.appcontrol.core.restore

import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.isAutomationUnusable
import eu.darken.sdmse.common.error.HasLocalizedError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RestoreUnavailableExceptionTest : BaseTest() {

    private val exception = RestoreUnavailableException("Restore button is disabled")

    @Test
    fun `aborts the plan as a failure`() {
        exception.shouldBeInstanceOf<PlanAbortException>()
        exception.treatAsSuccess shouldBe false
    }

    @Test
    fun `carries a localized error`() {
        exception.shouldBeInstanceOf<HasLocalizedError>()
    }

    @Test
    fun `does not spend the automation failure budget`() {
        exception.isAutomationUnusable() shouldBe false
    }
}
