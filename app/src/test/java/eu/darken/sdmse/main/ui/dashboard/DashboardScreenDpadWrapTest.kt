package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalTourTargetRegistry
import eu.darken.sdmse.common.compose.tour.TourTargetRegistry
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.cards.SetupDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.setup.SetupModule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * D-pad wrap-around on the dashboard: UP at the topmost grid card jumps to the bottom dock,
 * DOWN from the bar buttons jumps back into the grid. Mid-list UP must keep navigating cards.
 */
class DashboardScreenDpadWrapTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settingsLabel get() = context.getString(CommonR.string.general_settings_title)
    private val upgradeLabel get() = context.getString(R.string.upgrades_dashcard_upgrade_action)
    private val scanLabel get() = context.getString(CommonR.string.general_scan_action)
    private val firstToolTitle get() = context.getString(CommonR.string.corpsefinder_tool_name)
    private val secondToolTitle get() = context.getString(CommonR.string.systemcleaner_tool_name)

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

    private fun bottomBarState(
        action: BottomBarState.Action = BottomBarState.Action.SCAN,
        heroSummary: HeroSummary? = null,
    ) = BottomBarState(
        isReady = true,
        actionState = action,
        activeTasks = 0,
        queuedTasks = 0,
        heroSummary = heroSummary,
        upgradeInfo = null,
    )

    private fun defaultItems() = listOf(
        toolItem(SDMTool.Type.CORPSEFINDER),
        toolItem(SDMTool.Type.SYSTEMCLEANER),
    )

    @Test
    fun `UP at the topmost card wraps focus into the bottom dock`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(firstToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        // Entering the dock group picks the main-action FAB when it is present and active.
        composeRule.assertFocusedWithin(hasContentDescription(scanLabel))
    }

    @Test
    fun `DOWN from the settings button wraps back into the grid`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithContentDescription(settingsLabel).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_DOWN)

        composeRule.assertFocusedWithin(hasText(firstToolTitle))
    }

    @Test
    fun `UP mid-list moves within the grid instead of wrapping`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(secondToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasText(firstToolTitle))
    }

    @Test
    fun `wrap still lands on a dock control while the FAB is inert in WORKING state`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(action = BottomBarState.Action.WORKING),
        )
        composeRule.onNodeWithText(firstToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasContentDescription(upgradeLabel))
    }

    @Test
    fun `wrap works with the hero card visible`() {
        val heroSummary = HeroSummary(
            mode = HeroSummary.Mode.FREEABLE,
            totalSize = 1024L * 1024L,
            itemCount = 3,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1024L * 1024L, 3),
            ),
        )
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(heroSummary = heroSummary),
        )
        composeRule.onNodeWithText(firstToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        // With the hero card shown, entering the dock lands on the hero's tool chip (it has a
        // content description, the grid card title is plain text — the matcher can't false-match).
        composeRule.assertFocusedWithin(hasContentDescription(firstToolTitle))
    }

    @Test
    fun `LEFT at the horizontal edge jumps to the bottom dock`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(secondToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_LEFT)

        composeRule.assertFocusedWithin(hasContentDescription(scanLabel))
    }

    @Test
    fun `RIGHT at the horizontal edge jumps to the bottom dock`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(secondToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_RIGHT)

        composeRule.assertFocusedWithin(hasContentDescription(scanLabel))
    }

    @Test
    fun `LEFT cycles from a card to the dock and back to the same card`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(secondToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.assertFocusedWithin(hasContentDescription(scanLabel))

        // No dock control sits left of the FAB, so another LEFT hops back into the grid —
        // restoring the card we left, not the topmost one.
        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.assertFocusedWithin(hasText(secondToolTitle))
    }

    @Test
    fun `return restores the remembered card even when a different card is spatially closer`() {
        // Three cards, the MIDDLE one remembered: a spatial-search return (the bug observed on
        // TV) would land on the bottom card nearest the FAB, and a default group enter would
        // land on the top card. Only a genuine restore brings back the middle one.
        val items = listOf(
            toolItem(SDMTool.Type.CORPSEFINDER),
            toolItem(SDMTool.Type.SYSTEMCLEANER),
            toolItem(SDMTool.Type.APPCLEANER),
        )
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = items),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(secondToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.assertFocusedWithin(hasContentDescription(scanLabel))

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)
        composeRule.assertFocusedWithin(hasText(secondToolTitle))
    }

    @Test
    fun `UP from the dock returns to the previously focused card`() {
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(secondToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.assertFocusedWithin(hasContentDescription(scanLabel))

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)
        composeRule.assertFocusedWithin(hasText(secondToolTitle))
    }

    @Test
    fun `focus entering the incomplete-setup card lands on Continue, Dismiss stays reachable`() {
        val continueLabel = context.getString(R.string.setup_incomplete_card_continue_action)
        val dismissLabel = context.getString(CommonR.string.general_dismiss_action)
        val incompleteModule = object : SetupModule.State.Current {
            override val type: SetupModule.Type = SetupModule.Type.STORAGE
            override val isComplete: Boolean = false
        }
        val setupItem = SetupDashboardCardItem(
            setupState = SetupManager.State(
                moduleStates = listOf(incompleteModule),
                isDismissed = false,
                isHealerWorking = false,
            ),
            onDismiss = {},
            onContinue = {},
        )
        composeRule.setDashboardContent(
            listState = DashboardViewModel.ListState(items = listOf(setupItem) + defaultItems()),
            bottomBarState = bottomBarState(),
        )
        composeRule.onNodeWithText(firstToolTitle).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithText(continueLabel).assertIsFocused()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithText(dismissLabel).assertIsFocused()
    }
}

private fun ComposeContentTestRule.setDashboardContent(
    listState: DashboardViewModel.ListState,
    bottomBarState: BottomBarState,
) {
    val controller = mockk<GuidedTourController>(relaxed = true).also {
        coEvery { it.shouldStart(any()) } returns false
        every { it.session } returns MutableStateFlow(null)
    }
    setContent {
        PreviewWrapper {
            CompositionLocalProvider(
                LocalTourTargetRegistry provides TourTargetRegistry(),
                LocalGuidedTourController provides controller,
            ) {
                DashboardScreen(
                    listState = listState,
                    bottomBarState = bottomBarState,
                )
            }
        }
    }
    waitForIdle()
}

// Dispatched to the focused node, not onRoot(): focusing a tooltip-wrapped button (Settings,
// Upgrade) opens its tooltip popup, which adds a second root and makes onRoot() ambiguous.
private fun ComposeContentTestRule.pressKey(keyCode: Int) {
    onNode(isFocused()).performKeyPress(ComposeKeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, keyCode)))
    onNode(isFocused()).performKeyPress(ComposeKeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_UP, keyCode)))
    waitForIdle()
}

/**
 * Asserts that *some* node is focused and that it is, or sits inside, a node matching [matcher].
 * Focus may land on a container (a card) or one of its actions (a button) depending on traversal
 * heuristics; both count as "focus arrived at the right control cluster".
 */
private fun ComposeContentTestRule.assertFocusedWithin(matcher: SemanticsMatcher) {
    onNode(isFocused()).assert(matcher.or(hasAnyAncestor(matcher)).or(hasAnyDescendant(matcher)))
}
