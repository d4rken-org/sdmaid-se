package eu.darken.sdmse.systemcleaner.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SystemCleanerSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: SystemCleanerSettingsViewModel.State,
        onScreenshotsAgeSaved: (java.time.Duration) -> Unit = {},
        onRootFilterBadgeClick: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                SystemCleanerSettingsScreen(
                    state = state,
                    onScreenshotsAgeSaved = onScreenshotsAgeSaved,
                    onRootFilterBadgeClick = onRootFilterBadgeClick,
                )
            }
        }
    }

    // The settings screen uses a LazyColumn; rows below the viewport aren't composed until
    // scrolled into view.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `top bar shows the SystemCleaner title`() {
        composeRule.setSettingsScreen(SystemCleanerSettingsViewModel.State())

        composeRule.onNodeWithText("SystemCleaner").assertExists()
    }

    @Test
    fun `superfluous APKs sub-row hidden when filter is off`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterSuperfluosApks = false),
        )

        // The "Include same version" row is conditional on filterSuperfluosApks. When the
        // parent toggle is off, this row should not be rendered at all.
        composeRule.onAllNodesWithText("Include same version").assertCountEquals(0)
    }

    @Test
    fun `superfluous APKs sub-row visible when filter is on`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterSuperfluosApks = true),
        )

        composeRule.scrollToText("Include same version")
        composeRule.onNodeWithText("Include same version").assertExists()
    }

    @Test
    fun `screenshots age row hidden when screenshots filter is off`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterScreenshots = false),
        )

        composeRule.onAllNodesWithText("Screenshot Age").assertCountEquals(0)
    }

    @Test
    fun `screenshots age row visible when screenshots filter is on`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterScreenshots = true),
        )

        composeRule.scrollToText("Screenshot Age")
        composeRule.onNodeWithText("Screenshot Age").assertExists()
    }

    @Test
    fun `root-gated row shows Set up badge when root is unavailable`() {
        // ANR is the first root-gated filter (first item in the "specific filters" category).
        // When areSystemFilterAvailable=false, the row renders a "Set up" badge over its switch.
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(
                areSystemFilterAvailable = false,
                filterAnr = false,
            ),
        )

        // Scroll to the ANR row title so it's composed.
        composeRule.scrollToText("ANR errors")
        // The badge icon's content description is the localized "Set up" string from
        // SettingsBadgedSwitchItem. Multiple gated rows render the badge so we expect ≥ 1.
        composeRule.onAllNodesWithContentDescription("Set up").fetchSemanticsNodes().size.let {
            if (it == 0) throw AssertionError("Expected at least one Set up badge visible")
        }
    }

    @Test
    fun `root-gated row badge tap invokes onRootFilterBadgeClick callback`() {
        var badgeClicks = 0
        composeRule.setSettingsScreen(
            state = SystemCleanerSettingsViewModel.State(
                areSystemFilterAvailable = false,
                filterAnr = false,
            ),
            onRootFilterBadgeClick = { badgeClicks++ },
        )

        // Scroll to and tap the ANR row title — when gated, tapping the row routes to
        // `onBadgeClick` (badgeClick takes the row click instead of toggling the switch).
        composeRule.scrollToText("ANR errors")
        composeRule.onNodeWithText("ANR errors").performClick()

        if (badgeClicks < 1) throw AssertionError("Expected badge click to fire, got $badgeClicks")
    }
}
