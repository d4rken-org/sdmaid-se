package eu.darken.sdmse.main.ui.settings.general

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class GeneralSettingsScreenTourRowTest : BaseComposeRobolectricTest() {

    @Test
    fun `Reset guided tours row is visible and click invokes the lambda`() {
        var invoked = 0
        composeRule.setContent {
            PreviewWrapper {
                GeneralSettingsScreen(
                    state = GeneralSettingsViewModel.State(),
                    onResetGuidedTours = { invoked++ },
                )
            }
        }
        // The settings screen uses a LazyColumn — the row sits near the bottom and isn't
        // composed at the default viewport size. Scroll the LazyColumn to it first.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Reset guided tours"))
        composeRule.onNodeWithText("Reset guided tours").performClick()
        composeRule.waitForIdle()
        if (invoked != 1) error("expected onResetGuidedTours to fire once, got $invoked")
    }
}


