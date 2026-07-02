package eu.darken.sdmse.scheduler.ui.manager

import eu.darken.sdmse.main.core.taskmanager.AcsScheduleRisk
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AcsScreenLockedHintTest : BaseTest() {

    private fun resolve(
        risk: AcsScheduleRisk = AcsScheduleRisk.ACS_REQUIRED_ALL,
        schedulerUsesAutomation: Boolean = true,
        hasAcsConsent: Boolean = true,
        hasEnabledAppCleanerSchedule: Boolean = true,
        dismissed: Boolean = false,
    ) = resolveAcsScreenLockedHint(
        risk = risk,
        schedulerUsesAutomation = schedulerUsesAutomation,
        hasAcsConsent = hasAcsConsent,
        hasEnabledAppCleanerSchedule = hasEnabledAppCleanerSchedule,
        dismissed = dismissed,
    )

    @Test fun `all conditions met - surfaces the underlying risk`() {
        resolve(risk = AcsScheduleRisk.ACS_REQUIRED_ALL) shouldBe AcsScheduleRisk.ACS_REQUIRED_ALL
        resolve(risk = AcsScheduleRisk.ACS_REQUIRED_SYSTEM_APPS_ONLY) shouldBe
            AcsScheduleRisk.ACS_REQUIRED_SYSTEM_APPS_ONLY
    }

    @Test fun `no underlying risk - hidden`() {
        resolve(risk = AcsScheduleRisk.NONE) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `scheduler automation off - hidden`() {
        resolve(schedulerUsesAutomation = false) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `no accessibility consent - hidden`() {
        resolve(hasAcsConsent = false) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `no enabled appcleaner schedule - hidden`() {
        resolve(hasEnabledAppCleanerSchedule = false) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `dismissed - hidden`() {
        resolve(dismissed = true) shouldBe AcsScheduleRisk.NONE
    }
}
