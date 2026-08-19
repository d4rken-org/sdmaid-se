package eu.darken.sdmse.analyzer.ui.storage.device

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DeviceStorageScreenTest : BaseComposeRobolectricTest() {

    // Relaxed mock: shouldStart() defaults to false, so no guided tour starts.
    private val mockTourController: GuidedTourController = mockk(relaxed = true)

    private fun ComposeContentTestRule.setStorageScreen(
        state: DeviceStorageViewModel.State,
        onLowSpaceHintDismiss: () -> Unit = {},
        onLowSpaceHintUpgrade: () -> Unit = {},
    ) {
        setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    DeviceStorageScreen(
                        stateSource = MutableStateFlow(state),
                        onLowSpaceHintDismiss = onLowSpaceHintDismiss,
                        onLowSpaceHintUpgrade = onLowSpaceHintUpgrade,
                    )
                }
            }
        }
    }

    @Test
    fun `the low space hint renders when not Pro and not dismissed`() {
        composeRule.setStorageScreen(DeviceStorageViewModel.State(showLowSpaceHint = true))

        composeRule.onNodeWithText("Never get caught out of space").assertExists()
    }

    @Test
    fun `the low space hint stays away when hidden`() {
        composeRule.setStorageScreen(DeviceStorageViewModel.State(showLowSpaceHint = false))

        composeRule.onNodeWithText("Never get caught out of space").assertDoesNotExist()
    }

    @Test
    fun `the hint's buttons are wired`() {
        var dismissals = 0
        var upgrades = 0
        composeRule.setStorageScreen(
            state = DeviceStorageViewModel.State(showLowSpaceHint = true),
            onLowSpaceHintDismiss = { dismissals++ },
            onLowSpaceHintUpgrade = { upgrades++ },
        )

        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.onNodeWithText("Upgrade").performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(1, upgrades)
        }
    }
}
