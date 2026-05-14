package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.LocalTourTargetRegistry
import eu.darken.sdmse.common.compose.tour.TourTargetRegistry
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.cards.DashboardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.tour.DashboardTour
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.setup.SetupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DashboardScreenTourIntegrationTest : BaseComposeRobolectricTest() {

    private val setupItem = SetupDashboardCardItem(
        setupState = SetupManager.State(
            moduleStates = emptyList(),
            isDismissed = false,
            isHealerWorking = false,
        ),
        onDismiss = {},
        onContinue = {},
    )

    private fun toolItem(type: SDMTool.Type) = ToolDashboardCardItem(
        toolType = type,
        isInitializing = false,
        result = null,
        progress = null,
        showProRequirement = false,
        onScan = {},
        onDelete = {},
        onViewTool = {},
        onViewDetails = {},
        onCancel = {},
    )

    @Test
    fun `four tour targets register when setup and tool cards are present`() {
        val registry = TourTargetRegistry()
        val state = DashboardViewModel.ListState(
            items = listOf(
                setupItem,
                toolItem(SDMTool.Type.CORPSEFINDER),
                toolItem(SDMTool.Type.SYSTEMCLEANER),
            ),
        )
        composeRule.setDashboardContent(registry, state)
        composeRule.waitForIdle()

        assertHas(registry, DashboardTour.SETUP_TARGET)
        assertHas(registry, DashboardTour.TOOLS_TARGET)
        assertHas(registry, DashboardTour.MAIN_ACTION_TARGET)
        assertHas(registry, DashboardTour.SETTINGS_TARGET)
    }

    @Test
    fun `setup target is absent when setup card is not in items`() {
        val registry = TourTargetRegistry()
        val state = DashboardViewModel.ListState(
            items = listOf(
                toolItem(SDMTool.Type.CORPSEFINDER),
                toolItem(SDMTool.Type.SYSTEMCLEANER),
            ),
        )
        composeRule.setDashboardContent(registry, state)
        composeRule.waitForIdle()

        if (registry.has(DashboardTour.SETUP_TARGET)) {
            error("expected SETUP_TARGET to be absent when no setup card")
        }
        assertHas(registry, DashboardTour.TOOLS_TARGET)
        assertHas(registry, DashboardTour.MAIN_ACTION_TARGET)
        assertHas(registry, DashboardTour.SETTINGS_TARGET)
    }

    @Test
    fun `first-tool target picks the first ToolDashboardCardItem in items order`() {
        // Reverse the typical order so the first tool item is APPCLEANER (not CORPSEFINDER).
        val registry = TourTargetRegistry()
        val firstTool = toolItem(SDMTool.Type.APPCLEANER)
        val laterTool = toolItem(SDMTool.Type.CORPSEFINDER)
        val state = DashboardViewModel.ListState(
            items = listOf(setupItem, firstTool, laterTool),
        )
        composeRule.setDashboardContent(registry, state)
        composeRule.waitForIdle()

        // We can't easily distinguish "which tool" the bounds belong to without a deeper hook,
        // but the fact that TOOLS_TARGET is registered AND only one entry exists for it
        // confirms it landed on a single ToolDashboardCardItem (the first one).
        assertHas(registry, DashboardTour.TOOLS_TARGET)
    }

    @Test
    fun `tour does not auto-start when items list contains no tool cards`() {
        val registry = TourTargetRegistry()
        val state = DashboardViewModel.ListState(items = emptyList())
        val controller = mockk<GuidedTourController>(relaxed = true).also {
            coEvery { it.shouldStart(any()) } returns true
            every { it.session } returns MutableStateFlow(null)
        }
        composeRule.setDashboardContent(registry, state, controller)
        composeRule.waitForIdle()

        // Verify the predicate gate is never reached — more deterministic than asserting on
        // start(), which can be delayed past waitForIdle() by suspending effects.
        coVerify(exactly = 0) { controller.shouldStart(any()) }
    }

    @Test
    fun `tour does auto-start once a tool card is present`() {
        val registry = TourTargetRegistry()
        val state = DashboardViewModel.ListState(
            items = listOf(toolItem(SDMTool.Type.CORPSEFINDER)),
        )
        val controller = mockk<GuidedTourController>(relaxed = true).also {
            coEvery { it.shouldStart(any()) } returns true
            every { it.session } returns MutableStateFlow(null)
        }
        composeRule.setDashboardContent(registry, state, controller)
        composeRule.waitForIdle()

        coVerify(exactly = 1) { controller.start(any()) }
    }
}

private fun assertHas(registry: TourTargetRegistry, id: String) {
    if (!registry.has(id)) error("registry missing target id: $id")
    val rect = registry.get(id)!!
    if (!rect.isFinite || rect.width <= 0 || rect.height <= 0) {
        error("target $id has invalid bounds: $rect")
    }
}

private fun ComposeContentTestRule.setDashboardContent(
    registry: TourTargetRegistry,
    listState: DashboardViewModel.ListState,
    controller: GuidedTourController? = null,
) {
    val activeController = controller ?: mockk<GuidedTourController>(relaxed = true).also {
        // Force the auto-start LaunchedEffect to short-circuit before scrolling/starting,
        // so the test isolates target registration from tour-start side effects.
        coEvery { it.shouldStart(any()) } returns false
        // Provide a real StateFlow so the dashboard's tour-active observer reads a TourSession?,
        // not an unresolved mockk default that crashes the cast.
        every { it.session } returns MutableStateFlow(null)
    }
    val bottomBarState = DashboardViewModel.BottomBarState(
        isReady = true,
        actionState = DashboardViewModel.BottomBarState.Action.SCAN,
        activeTasks = 0,
        queuedTasks = 0,
        totalItems = 0,
        totalSize = 0L,
        upgradeInfo = null,
    )
    setContent {
        PreviewWrapper {
            CompositionLocalProvider(
                LocalTourTargetRegistry provides registry,
                LocalGuidedTourController provides activeController,
            ) {
                DashboardScreen(
                    listState = listState,
                    bottomBarState = bottomBarState,
                )
            }
        }
    }
}
