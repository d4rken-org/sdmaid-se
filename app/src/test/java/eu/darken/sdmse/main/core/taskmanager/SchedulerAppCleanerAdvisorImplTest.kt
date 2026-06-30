package eu.darken.sdmse.main.core.taskmanager

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SchedulerAppCleanerAdvisorImplTest : BaseTest() {

    private fun classify(
        includeInaccessible: Boolean = true,
        filterPublic: Boolean = true,
        filterPrivate: Boolean = true,
        hasRoot: Boolean = false,
        hasAdb: Boolean = false,
        includeSystemApps: Boolean = false,
    ) = classifyAcsScheduleRisk(
        includeInaccessible = includeInaccessible,
        filterPublic = filterPublic,
        filterPrivate = filterPrivate,
        hasRoot = hasRoot,
        hasAdb = hasAdb,
        includeSystemApps = includeSystemApps,
    )

    @Test fun `no privileged access, inaccessible cleaning on - ACS needed for all`() {
        classify() shouldBe AcsScheduleRisk.ACS_REQUIRED_ALL
    }

    @Test fun `root available - no ACS, inaccessible detection is skipped`() {
        classify(hasRoot = true) shouldBe AcsScheduleRisk.NONE
        classify(hasRoot = true, hasAdb = true, includeSystemApps = true) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `adb covers normal apps - no ACS when system apps excluded`() {
        classify(hasAdb = true, includeSystemApps = false) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `adb present but system apps included - ACS needed for system apps only`() {
        classify(hasAdb = true, includeSystemApps = true) shouldBe AcsScheduleRisk.ACS_REQUIRED_SYSTEM_APPS_ONLY
    }

    @Test fun `inaccessible cleaning disabled - no ACS`() {
        classify(includeInaccessible = false) shouldBe AcsScheduleRisk.NONE
    }

    @Test fun `default cache filters disabled - no ACS, scanner finds no inaccessible targets`() {
        classify(filterPublic = false) shouldBe AcsScheduleRisk.NONE
        classify(filterPrivate = false) shouldBe AcsScheduleRisk.NONE
    }
}
