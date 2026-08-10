package eu.darken.sdmse.main.ui.settings.general

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class GeneralSettingsScreenSummaryAutoShowRowTest : BaseComposeRobolectricTest() {

    @Test
    fun `Show summary automatically row renders and toggling it reports the new value`() {
        val values = mutableListOf<Boolean>()
        composeRule.setContent {
            PreviewWrapper {
                GeneralSettingsScreen(
                    state = GeneralSettingsViewModel.State(dashboardSummaryAutoShow = true),
                    onDashboardSummaryAutoShowChanged = { values.add(it) },
                )
            }
        }
        // The settings screen uses a LazyColumn — scroll it to the row before touching it.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Show summary automatically"))
        composeRule.onNodeWithText("Show summary automatically").performClick()
        composeRule.waitForIdle()
        if (values != listOf(false)) error("expected onDashboardSummaryAutoShowChanged(false), got $values")
    }
}
