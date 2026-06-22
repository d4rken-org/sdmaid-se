package eu.darken.sdmse.deduplicator.ui.settings

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DeduplicatorSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: DeduplicatorSettingsViewModel.State = DeduplicatorSettingsViewModel.State(),
        onAllowDeleteAllChanged: (Boolean) -> Unit = {},
        onSkipUncommonChanged: (Boolean) -> Unit = {},
        onSleuthChecksumChanged: (Boolean) -> Unit = {},
        onSleuthPHashChanged: (Boolean) -> Unit = {},
        onSleuthMediaChanged: (Boolean) -> Unit = {},
        onSearchLocationsClick: () -> Unit = {},
        onArbiterConfigClick: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                DeduplicatorSettingsScreen(
                    state = state,
                    onAllowDeleteAllChanged = onAllowDeleteAllChanged,
                    onSkipUncommonChanged = onSkipUncommonChanged,
                    onSleuthChecksumChanged = onSleuthChecksumChanged,
                    onSleuthPHashChanged = onSleuthPHashChanged,
                    onSleuthMediaChanged = onSleuthMediaChanged,
                    onSearchLocationsClick = onSearchLocationsClick,
                    onArbiterConfigClick = onArbiterConfigClick,
                )
            }
        }
    }

    // Settings screen uses a LazyColumn; rows below the viewport aren't composed until scrolled
    // into view. Helper locates the scrollable container and scrolls until the node is visible.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `top bar shows the Deduplicator title`() {
        composeRule.setSettingsScreen()

        composeRule.onNodeWithText("Deduplicator").assertExists()
    }

    @Test
    fun `search-locations row and arbiter row are visible at startup`() {
        composeRule.setSettingsScreen()

        // The first two rows in the LazyColumn — should be above the fold even on a small screen.
        composeRule.onNodeWithText("Search locations").assertExists()
        composeRule.onNodeWithText("Deletion strategy").assertExists()
    }

    @Test
    fun `tapping the search-locations row invokes onSearchLocationsClick`() {
        var clicks = 0
        composeRule.setSettingsScreen(onSearchLocationsClick = { clicks++ })

        composeRule.onNodeWithText("Search locations").performClick()

        if (clicks < 1) throw AssertionError("Expected at least one click but got $clicks")
    }

    @Test
    fun `tapping the arbiter-config row invokes onArbiterConfigClick`() {
        var clicks = 0
        composeRule.setSettingsScreen(onArbiterConfigClick = { clicks++ })

        composeRule.onNodeWithText("Deletion strategy").performClick()

        if (clicks < 1) throw AssertionError("Expected at least one click but got $clicks")
    }

    @Test
    fun `tapping the Make Delete-all possible switch invokes its callback with the inverted value`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = DeduplicatorSettingsViewModel.State(allowDeleteAll = false),
            onAllowDeleteAllChanged = { captured = it },
        )

        composeRule.scrollToText("Make \"Delete all\" possible")
        composeRule.onNodeWithText("Make \"Delete all\" possible").performClick()

        if (captured != true) throw AssertionError("Expected callback with true, got $captured")
    }

    @Test
    fun `Detection method category header is rendered`() {
        composeRule.setSettingsScreen()
        composeRule.scrollToText("Detection method")

        composeRule.onNodeWithText("Detection method").assertExists()
    }

    @Test
    fun `tapping the Content-checksum sleuth row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = DeduplicatorSettingsViewModel.State(isSleuthChecksumEnabled = true),
            onSleuthChecksumChanged = { captured = it },
        )

        composeRule.scrollToText("Content checksum")
        composeRule.onNodeWithText("Content checksum").performClick()

        if (captured != false) throw AssertionError("Expected callback with false (inverted from true), got $captured")
    }

    @Test
    fun `tapping the Perceptual-hash sleuth row toggles its callback`() {
        // PHash and Media have their own callbacks; a regression that wired them to the wrong
        // setter (e.g. swapped phash and media on copy-paste) would only fail with per-row tests.
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = DeduplicatorSettingsViewModel.State(isSleuthPHashEnabled = false),
            onSleuthPHashChanged = { captured = it },
        )

        composeRule.scrollToText("Perceptual hash")
        composeRule.onNodeWithText("Perceptual hash").performClick()

        if (captured != true) throw AssertionError("Expected callback with true, got $captured")
    }

    @Test
    fun `tapping the Media-fingerprint sleuth row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = DeduplicatorSettingsViewModel.State(isSleuthMediaEnabled = false),
            onSleuthMediaChanged = { captured = it },
        )

        composeRule.scrollToText("Media fingerprint")
        composeRule.onNodeWithText("Media fingerprint").performClick()

        if (captured != true) throw AssertionError("Expected callback with true, got $captured")
    }

    @Test
    fun `tapping the Skip-uncommon switch toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = DeduplicatorSettingsViewModel.State(skipUncommon = true),
            onSkipUncommonChanged = { captured = it },
        )

        composeRule.scrollToText("Skip uncommon file types")
        composeRule.onNodeWithText("Skip uncommon file types").performClick()

        if (captured != false) throw AssertionError("Expected callback with false (inverted from true), got $captured")
    }
}
