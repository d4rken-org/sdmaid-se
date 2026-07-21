package eu.darken.sdmse.main.ui.dashboard.tour

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DashboardTourTest : BaseTest() {

    @Test
    fun `definition includes all six steps by default`() {
        val def = DashboardTour.definition()
        def.steps.map { it.targetId } shouldBe listOf(
            null,
            DashboardTour.SETUP_TARGET,
            DashboardTour.TOOLS_TARGET,
            DashboardTour.MAIN_ACTION_TARGET,
            DashboardTour.MANUAL_TOOL_TARGET,
            DashboardTour.SETTINGS_TARGET,
        )
    }

    @Test
    fun `overview is always the first step and is centerless`() {
        val def = DashboardTour.definition()
        val first = def.steps.first()
        first.stepId shouldBe "overview"
        first.targetId shouldBe null
    }

    @Test
    fun `setup step is dropped when setup card is hidden`() {
        val def = DashboardTour.definition(includeSetup = false)
        def.steps.map { it.targetId } shouldBe listOf(
            null,
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
            null,
            DashboardTour.SETUP_TARGET,
            DashboardTour.TOOLS_TARGET,
            DashboardTour.MAIN_ACTION_TARGET,
            DashboardTour.SETTINGS_TARGET,
        )
    }

    @Test
    fun `both setup and manual-tools dropped collapses to overview plus three core steps`() {
        val def = DashboardTour.definition(
            includeSetup = false,
            includeManualTool = false,
        )
        def.steps.map { it.targetId } shouldBe listOf(
            null,
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

    @Test
    fun `prepareTools is wired only on the tools step`() {
        var called = false
        val hook: suspend () -> Unit = { called = true }
        val def = DashboardTour.definition(prepareTools = hook, prepareManualTool = null)

        val withHook = def.steps.filter { it.prepareTarget != null }.map { it.stepId }
        withHook shouldBe listOf("tools")
        called shouldBe false // the hook is referenced, not invoked
    }

    @Test
    fun `prepareSetup is wired only on the setup step`() {
        var called = false
        val hook: suspend () -> Unit = { called = true }
        val def = DashboardTour.definition(
            prepareSetup = hook,
            prepareTools = null,
            prepareManualTool = null,
        )

        val withHook = def.steps.filter { it.prepareTarget != null }.map { it.stepId }
        withHook shouldBe listOf("setup")
        called shouldBe false // the hook is referenced, not invoked
    }

    @Test
    fun `prepareSetup is dropped when setup step is excluded`() {
        var called = false
        val hook: suspend () -> Unit = { called = true }
        val def = DashboardTour.definition(
            includeSetup = false,
            prepareSetup = hook,
            prepareTools = null,
            prepareManualTool = null,
        )

        def.steps.any { it.stepId == "setup" } shouldBe false
        def.steps.any { it.prepareTarget != null } shouldBe false
        called shouldBe false
    }

    @Test
    fun `onComplete is wired to the tour definition`() {
        var called = false
        val hook: suspend () -> Unit = { called = true }
        val def = DashboardTour.definition(onComplete = hook)

        def.onComplete shouldBe hook
        called shouldBe false
    }
}
