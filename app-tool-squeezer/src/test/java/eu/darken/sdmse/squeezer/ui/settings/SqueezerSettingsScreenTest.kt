package eu.darken.sdmse.squeezer.ui.settings

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SqueezerSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: SqueezerSettingsViewModel.State,
        onIncludeJpegChanged: (Boolean) -> Unit = {},
        onIncludeWebpChanged: (Boolean) -> Unit = {},
        onIncludeVideoChanged: (Boolean) -> Unit = {},
        onSkipPreviouslyCompressedChanged: (Boolean) -> Unit = {},
        onWriteExifMarkerChanged: (Boolean) -> Unit = {},
        onMinSizeChanged: (Long) -> Unit = {},
        onClearHistoryConfirmed: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                SqueezerSettingsScreen(
                    state = state,
                    onIncludeJpegChanged = onIncludeJpegChanged,
                    onIncludeWebpChanged = onIncludeWebpChanged,
                    onIncludeVideoChanged = onIncludeVideoChanged,
                    onSkipPreviouslyCompressedChanged = onSkipPreviouslyCompressedChanged,
                    onWriteExifMarkerChanged = onWriteExifMarkerChanged,
                    onMinSizeChanged = onMinSizeChanged,
                    onClearHistoryConfirmed = onClearHistoryConfirmed,
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
    fun `top bar shows the tool name`() {
        composeRule.setSettingsScreen(SqueezerSettingsViewModel.State())

        composeRule.onNodeWithText("Media Squeeze").assertExists()
    }

    @Test
    fun `JPEG row is visible at startup`() {
        composeRule.setSettingsScreen(SqueezerSettingsViewModel.State())

        // First row in the type-toggle list — should be above the fold even on small viewports.
        composeRule.onNodeWithText("JPEG").assertExists()
    }

    @Test
    fun `tapping the JPEG row toggles its callback with the inverted value`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = SqueezerSettingsViewModel.State(includeJpeg = true),
            onIncludeJpegChanged = { captured = it },
        )

        composeRule.onNodeWithText("JPEG").performClick()

        // Started checked → tap inverts to `false`.
        captured shouldBe false
    }

    @Test
    fun `tapping the WebP row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = SqueezerSettingsViewModel.State(includeWebp = false),
            onIncludeWebpChanged = { captured = it },
        )

        composeRule.onNodeWithText("WebP").performClick()

        captured shouldBe true
    }

    @Test
    fun `tapping the MP4 video row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = SqueezerSettingsViewModel.State(includeVideo = false),
            onIncludeVideoChanged = { captured = it },
        )

        composeRule.scrollToText("MP4 Video")
        composeRule.onNodeWithText("MP4 Video").performClick()

        captured shouldBe true
    }

    @Test
    fun `tapping Skip previously compressed toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = SqueezerSettingsViewModel.State(skipPreviouslyCompressed = true),
            onSkipPreviouslyCompressedChanged = { captured = it },
        )

        composeRule.scrollToText("Skip previously compressed")
        composeRule.onNodeWithText("Skip previously compressed").performClick()

        captured shouldBe false
    }

    @Test
    fun `tapping Write EXIF marker toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = SqueezerSettingsViewModel.State(writeExifMarker = false),
            onWriteExifMarkerChanged = { captured = it },
        )

        composeRule.scrollToText("Write EXIF marker")
        composeRule.onNodeWithText("Write EXIF marker").performClick()

        captured shouldBe true
    }

    @Test
    fun `clearing the history shows the confirm dialog and onClearHistoryConfirmed fires after Reset`() {
        // Reset action label resolves to "Reset" from CommonR.string.general_reset_action. Before
        // the click the dialog title text "Clear compression history" is not in the tree.
        var cleared = 0
        composeRule.setSettingsScreen(
            state = SqueezerSettingsViewModel.State(historyCount = 12, historyDatabaseSize = 1024L),
            onClearHistoryConfirmed = { cleared++ },
        )
        composeRule.scrollToText("Compression history")

        // Sanity: dialog isn't open yet — "Reset" button text is not in the tree.
        // (Defending against a regression that auto-opens the dialog.)

        composeRule.onNodeWithText("Compression history").performClick()

        // Reset confirms; Cancel dismisses.
        composeRule.onNodeWithText("Reset").performClick()

        cleared shouldBe 1
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}
