package eu.darken.sdmse.common.debug.logviewer.ui

import android.content.Context
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logviewer.core.LogLine
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

class FloatingLogPanelTest : BaseComposeRobolectricTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = context.getString(id)

    private val sampleState = FloatingLogPanelViewModel.State(
        lines = listOf(
            LogLine(1, Logging.Priority.DEBUG, "Tag", "hello world"),
            LogLine(2, Logging.Priority.ERROR, "Tag", "boom"),
        ),
    )

    @Test
    fun `pause invokes callback from the overflow menu`() {
        var paused = 0
        composeRule.setContent {
            PreviewWrapper {
                FloatingLogPanel(
                    stateSource = MutableStateFlow(sampleState),
                    onTogglePause = { paused++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_more_action)).performClick()
        composeRule.onNodeWithText(str(R.string.debug_logview_pause_action)).performClick()
        composeRule.runOnIdle { assertEquals(1, paused) }
    }

    @Test
    fun `close invokes callback from the overflow menu`() {
        var closed = 0
        composeRule.setContent {
            PreviewWrapper {
                FloatingLogPanel(
                    stateSource = MutableStateFlow(sampleState),
                    onClose = { closed++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_more_action)).performClick()
        // Close lives at the bottom of a long menu — scroll it into view before clicking.
        composeRule.onNodeWithText(str(R.string.debug_logview_close_action)).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, closed) }
    }

    @Test
    fun `log level opens a dialog and selecting a level invokes callback`() {
        var picked: Logging.Priority? = null
        composeRule.setContent {
            PreviewWrapper {
                FloatingLogPanel(
                    stateSource = MutableStateFlow(sampleState),
                    onSetLevel = { picked = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_more_action)).performClick()
        // The menu item opens the level dialog; pick a level there. displayName(WARN) == "Warn".
        composeRule.onNodeWithText(str(R.string.debug_logview_level_action)).performClick()
        composeRule.onNodeWithText("Warn").performClick()
        composeRule.runOnIdle { assertEquals(Logging.Priority.WARN, picked) }
    }

    @Test
    fun `clear invokes callback from the overflow menu`() {
        var cleared = 0
        composeRule.setContent {
            PreviewWrapper {
                FloatingLogPanel(
                    stateSource = MutableStateFlow(sampleState),
                    onClear = { cleared++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_more_action)).performClick()
        composeRule.onNodeWithText(str(R.string.debug_logview_clear_action)).performClick()
        composeRule.runOnIdle { assertEquals(1, cleared) }
    }

    @Test
    fun `search row toggles from overflow and next-prev invoke callbacks`() {
        var next = 0
        var prev = 0
        val state = sampleState.copy(
            query = "hello",
            matchCount = 1,
            currentOrdinal = 1,
            currentMatchLineId = 1,
        )
        composeRule.setContent {
            PreviewWrapper {
                FloatingLogPanel(
                    stateSource = MutableStateFlow(state),
                    onNextMatch = { next++ },
                    onPrevMatch = { prev++ },
                )
            }
        }

        // Search is hidden by default — reveal it via the overflow menu.
        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_more_action)).performClick()
        composeRule.onNodeWithText(str(CommonR.string.general_search_action)).performClick()

        // With a non-blank query the match-nav buttons are now present.
        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_search_next_action)).performClick()
        composeRule.onNodeWithContentDescription(str(R.string.debug_logview_search_prev_action)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, next)
            assertEquals(1, prev)
        }
    }
}
