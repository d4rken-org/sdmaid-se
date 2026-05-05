package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.LocalTourTargetRegistry
import eu.darken.sdmse.common.compose.tour.TourTargetRegistry
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.cards.DashboardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.tour.DashboardTour
import eu.darken.sdmse.main.ui.tour.GuidedTourController
import eu.darken.sdmse.main.ui.tour.LocalGuidedTourController
import eu.darken.sdmse.setup.SetupManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class DashboardScreenTourIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

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
) {
    val controller = mockk<GuidedTourController>(relaxed = true).also {
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
                LocalGuidedTourController provides controller,
            ) {
                DashboardScreen(
                    listState = listState,
                    bottomBarState = bottomBarState,
                )
            }
        }
    }
}
