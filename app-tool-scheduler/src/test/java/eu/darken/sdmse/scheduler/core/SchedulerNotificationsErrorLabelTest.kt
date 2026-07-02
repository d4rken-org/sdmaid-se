package eu.darken.sdmse.scheduler.core

import eu.darken.sdmse.automation.core.errors.AutomationSchedulerException
import eu.darken.sdmse.automation.core.errors.ScreenUnavailableException
import eu.darken.sdmse.scheduler.R
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import eu.darken.sdmse.common.R as CommonR

class SchedulerNotificationsErrorLabelTest : BaseTest() {

    @Test fun `direct screen-unavailable exception is a skip`() {
        ScreenUnavailableException("Screen is unavailable!").isScreenUnavailableSkip() shouldBe true
    }

    @Test fun `scheduler-wrapped screen-unavailable exception is a skip`() {
        // This is exactly how SchedulerWorker wraps the failure before it reaches the notification.
        AutomationSchedulerException(ScreenUnavailableException("Screen is unavailable!"))
            .isScreenUnavailableSkip() shouldBe true
    }

    @Test fun `additionally wrapped screen-unavailable exception is a skip`() {
        RuntimeException(AutomationSchedulerException(ScreenUnavailableException("Screen is unavailable!")))
            .isScreenUnavailableSkip() shouldBe true
    }

    @Test fun `unrelated exception is not a skip`() {
        RuntimeException("Something else broke").isScreenUnavailableSkip() shouldBe false
    }

    @Test fun `error label resolves to the screen-unavailable string for a skip`() {
        AutomationSchedulerException(ScreenUnavailableException("Screen is unavailable!"))
            .toSchedulerErrorLabelRes() shouldBe R.string.scheduler_notification_result_skipped_screen_unavailable
    }

    @Test fun `error label resolves to the generic error string otherwise`() {
        RuntimeException("Something else broke")
            .toSchedulerErrorLabelRes() shouldBe CommonR.string.general_error_label
    }
}
