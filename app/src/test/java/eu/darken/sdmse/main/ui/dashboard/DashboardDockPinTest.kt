package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalTourTargetRegistry
import eu.darken.sdmse.common.compose.tour.TourTargetRegistry
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.setup.SetupManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

/**
 * On TV-style devices the grid scrolls as a side effect of D-pad focus movement — auto-hiding the
 * bottom dock on scroll would hide the very controls being navigated to. These tests pin down
 * that the dock survives scrolling when [DashboardScreen]'s isTv flag is set, while the touch
 * hide-on-scroll behavior stays intact otherwise.
 */
// Short viewport so the card list reliably overflows and scrolling is real, not a no-op.
@Config(qualifiers = "w320dp-h320dp")
class DashboardDockPinTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // Tool cards only exist for the four cleaning tools; the setup card on top guarantees the
    // grid overflows the test viewport, so "scrolling hides the dock" can't pass vacuously.
    private val cardItems = listOf(
        SetupDashboardCardItem(
            setupState = SetupManager.State(
                moduleStates = emptyList(),
                isDismissed = false,
                isHealerWorking = false,
            ),
            onDismiss = {},
            onContinue = {},
        ),
        toolItem(SDMTool.Type.CORPSEFINDER),
        toolItem(SDMTool.Type.SYSTEMCLEANER),
        toolItem(SDMTool.Type.APPCLEANER),
        toolItem(SDMTool.Type.DEDUPLICATOR),
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

    private fun setDashboard(isTv: Boolean) {
        val controller = mockk<GuidedTourController>(relaxed = true).also {
            coEvery { it.shouldStart(any()) } returns false
            every { it.session } returns MutableStateFlow(null)
        }
        val listState = DashboardViewModel.ListState(items = cardItems)
        val bottomBarState = BottomBarState(
            isReady = true,
            actionState = BottomBarState.Action.SCAN,
            activeTasks = 0,
            queuedTasks = 0,
            heroSummary = null,
            upgradeInfo = null,
        )
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(
                    LocalTourTargetRegistry provides TourTargetRegistry(),
                    LocalGuidedTourController provides controller,
                ) {
                    DashboardScreen(
                        listState = listState,
                        bottomBarState = bottomBarState,
                        isTv = isTv,
                    )
                }
            }
        }
    }

    private fun settingsNode() =
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_settings_title))

    // Scrolls forward to a MIDDLE card: scrolling to the last card overshoots and the grid clamps
    // back to max scroll, which the hide heuristic correctly reads as an upward scroll and
    // re-shows the dock — exactly the on-device behavior, but not what this test is probing.
    private fun scrollDown() {
        val grid = composeRule.onNode(hasScrollToIndexAction())
        // Guard against a vacuous pass: if everything fits the viewport, no scroll would happen
        // and the hide heuristic would (correctly) never trigger.
        val scrollRange = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        check(scrollRange.maxValue() > 0f) { "Grid content fits the viewport, scrolling would be a no-op" }
        grid.performScrollToIndex(cardItems.size / 2)
        composeRule.waitForIdle()
    }

    @Test
    fun `scrolling down hides the dock on touch devices`() {
        setDashboard(isTv = false)
        settingsNode().assertExists()

        scrollDown()

        settingsNode().assertDoesNotExist()
    }

    @Test
    fun `dock stays pinned on tv devices despite scrolling`() {
        setDashboard(isTv = true)
        settingsNode().assertExists()

        scrollDown()

        settingsNode().assertExists()
    }
}
