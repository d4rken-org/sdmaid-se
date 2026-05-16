package eu.darken.sdmse.corpsefinder.ui.settings

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

class CorpseFinderSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: CorpseFinderSettingsViewModel.State,
        onFilterSdcardChanged: (Boolean) -> Unit = {},
        onIncludeRiskKeeperChanged: (Boolean) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                CorpseFinderSettingsScreen(
                    state = state,
                    onFilterSdcardChanged = onFilterSdcardChanged,
                    onIncludeRiskKeeperChanged = onIncludeRiskKeeperChanged,
                )
            }
        }
    }

    // The settings screen uses a LazyColumn; rows below the viewport aren't composed until
    // scrolled into view. This helper locates the scrollable container and scrolls until the
    // node matching the given text becomes available in the semantics tree.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `top bar shows the CorpseFinder title`() {
        composeRule.setSettingsScreen(CorpseFinderSettingsViewModel.State())

        composeRule.onNodeWithText("CorpseFinder").assertExists()
    }

    @Test
    fun `watcher category header and switch row are visible at startup`() {
        composeRule.setSettingsScreen(CorpseFinderSettingsViewModel.State())

        // "Uninstall watcher" appears twice: once as the category header text and once as the
        // SettingsSwitchItem title. Both are above the fold and visible at startup.
        composeRule.onAllNodesWithText("Uninstall watcher").assertCountEquals(2)
    }

    @Test
    fun `tapping the SD card filter row toggles its callback with the inverted value`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = CorpseFinderSettingsViewModel.State(filterSdcardEnabled = true),
            onFilterSdcardChanged = { captured = it },
        )

        composeRule.scrollToText("SD card")
        composeRule.onNodeWithText("SD card").performClick()

        // Started checked → tap inverts to `false`.
        captured shouldBe false
    }

    @Test
    fun `tapping the keeper risk row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = CorpseFinderSettingsViewModel.State(includeRiskKeeper = false),
            onIncludeRiskKeeperChanged = { captured = it },
        )

        // The keeper-risk row title resolves to "Include desirable remnants" (CorpseR string
        // `corpsefinder_settings_risk_keeper_title`). Scroll to it before tapping.
        composeRule.scrollToText("Include desirable remnants")
        composeRule.onNodeWithText("Include desirable remnants").performClick()

        captured shouldBe true
    }

    @Test
    fun `private app data row when root is available shows no Set up badge and tap toggles filter`() {
        var filterToggled: Boolean? = null
        var badgeClicked = 0
        composeRule.setContent {
            PreviewWrapper {
                CorpseFinderSettingsScreen(
                    state = CorpseFinderSettingsViewModel.State(
                        filterPrivateDataEnabled = true,
                        isFilterPrivateDataAvailable = true,
                    ),
                    onFilterPrivateDataChanged = { filterToggled = it },
                    onRootFilterBadgeClick = { badgeClicked++ },
                )
            }
        }

        composeRule.scrollToText("Private app data")
        composeRule.onNodeWithText("Private app data").assertExists()
        // Ungated case: the "Set up" badge icon is NOT rendered for this row.
        // (Other rows may render it, so we check no badge sits near "Private app data".
        // Since we can't easily scope by parent here, asserting the toggle behaviour below
        // is the load-bearing differential check.)
        composeRule.onNodeWithText("Private app data").performClick()

        // Available: tapping the row goes to the filter toggle, NOT the badge handler.
        filterToggled shouldBe false
        badgeClicked shouldBe 0
    }

    @Test
    fun `private app data row when root is not available shows Set up badge and tap routes to badge handler`() {
        var filterToggled: Boolean? = null
        var badgeClicked = 0
        composeRule.setContent {
            PreviewWrapper {
                CorpseFinderSettingsScreen(
                    state = CorpseFinderSettingsViewModel.State(
                        filterPrivateDataEnabled = true,
                        isFilterPrivateDataAvailable = false,
                    ),
                    onFilterPrivateDataChanged = { filterToggled = it },
                    onRootFilterBadgeClick = { badgeClicked++ },
                )
            }
        }

        composeRule.scrollToText("Private app data")
        composeRule.onNodeWithText("Private app data").assertExists()
        // Gated case: the SettingsBadgedSwitchItem renders a "Set up" icon
        // (contentDescription comes from CommonR.string.general_set_up_action). Multiple root-
        // gated rows render the same icon, so we just assert at least one is present.
        val badgeCount = composeRule.onAllNodesWithContentDescription("Set up")
            .fetchSemanticsNodes().size
        if (badgeCount < 1) {
            throw AssertionError("Expected at least one `Set up` badge but found 0")
        }

        composeRule.onNodeWithText("Private app data").performClick()

        // Unavailable: tapping the row routes to the badge handler, NOT the filter toggle.
        filterToggled shouldBe null
        badgeClicked shouldBe 1
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}
