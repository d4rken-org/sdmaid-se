package eu.darken.sdmse.stats.ui.settings

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Duration

class StatsSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setScreen(
        onRetentionSnapshotsSaved: (Duration) -> Unit = {},
        onRetentionSnapshotsReset: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                StatsSettingsScreen(
                    stateSource = MutableStateFlow(StatsSettingsViewModel.State()),
                    onRetentionSnapshotsSaved = onRetentionSnapshotsSaved,
                    onRetentionSnapshotsReset = onRetentionSnapshotsReset,
                )
            }
        }
        onNode(hasScrollAction()).performScrollToNode(hasText("Storage history"))
    }

    private fun ComposeContentTestRule.openSnapshotsDialog() {
        onNodeWithText("Storage history").performClick()
        onNodeWithText("Save").assertExists()
    }

    @Test
    fun `storage history item shows the current retention`() {
        composeRule.setScreen()

        composeRule.onNodeWithText("Storage history").assertExists()
        composeRule.onNodeWithText("90 days").assertExists()
    }

    @Test
    fun `tapping storage history opens the age dialog`() {
        composeRule.setScreen()

        composeRule.onNodeWithText("Save").assertDoesNotExist()
        composeRule.openSnapshotsDialog()
    }

    @Test
    fun `saving the dialog passes the selected retention`() {
        val saved = mutableListOf<Duration>()
        composeRule.setScreen(onRetentionSnapshotsSaved = { saved += it })

        composeRule.openSnapshotsDialog()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle { assertEquals(listOf(Duration.ofDays(90)), saved) }
    }

    @Test
    fun `resetting the dialog invokes the reset callback`() {
        var resets = 0
        composeRule.setScreen(onRetentionSnapshotsReset = { resets++ })

        composeRule.openSnapshotsDialog()
        composeRule.onNodeWithText("Reset").performClick()

        composeRule.runOnIdle { assertEquals(1, resets) }
    }
}
