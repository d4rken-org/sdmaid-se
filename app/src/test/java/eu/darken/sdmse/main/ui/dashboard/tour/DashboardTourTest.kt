package eu.darken.sdmse.main.ui.dashboard.tour

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DashboardTourTest : BaseTest() {

    @Test
    fun `definition includes all five steps by default`() {
        val def = DashboardTour.definition()
        def.steps.map { it.targetId } shouldBe listOf(
            DashboardTour.SETUP_TARGET,
            DashboardTour.TOOLS_TARGET,
            DashboardTour.MAIN_ACTION_TARGET,
            DashboardTour.MANUAL_TOOL_TARGET,
            DashboardTour.SETTINGS_TARGET,
        )
    }

    @Test
    fun `setup step is dropped when setup card is hidden`() {
        val def = DashboardTour.definition(includeSetup = false)
        def.steps.map { it.targetId } shouldBe listOf(
            DashboardTour.TOOLS_TARGET,
            DashboardTour.MAIN_ACTION_TARGET,
            DashboardTour.MANUAL_TOOL_TARGET,
            DashboardTour.SETTINGS_TARGET,
        )
    }

    @Test
    fun `manual-tools step is dropped when swiper card is missing`() {
        val def = DashboardTour.definition(includeManualTool = false)
        def.steps.map { it.targetId } shouldBe listOf(
            DashboardTour.SETUP_TARGET,
            DashboardTour.TOOLS_TARGET,
            DashboardTour.MAIN_ACTION_TARGET,
            DashboardTour.SETTINGS_TARGET,
        )
    }

    @Test
    fun `both setup and manual-tools dropped collapses to three core steps`() {
        val def = DashboardTour.definition(
            includeSetup = false,
            includeManualTool = false,
        )
        def.steps.map { it.targetId } shouldBe listOf(
            DashboardTour.TOOLS_TARGET,
            DashboardTour.MAIN_ACTION_TARGET,
            DashboardTour.SETTINGS_TARGET,
        )
    }

    @Test
    fun `prepareManualTool is wired only on the manual-tools step`() {
        var called = false
        val hook: suspend () -> Unit = { called = true }
        val def = DashboardTour.definition(prepareManualTool = hook)

        val withHook = def.steps.filter { it.prepareTarget != null }.map { it.stepId }
        withHook shouldBe listOf("manualTools")
        called shouldBe false // the hook is referenced, not invoked
    }
}
