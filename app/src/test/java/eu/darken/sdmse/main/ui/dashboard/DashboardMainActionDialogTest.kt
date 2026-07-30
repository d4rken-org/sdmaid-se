package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The one-tap DELETE goes through a confirmation dialog, and only its confirm button may reach
 * `mainAction` — which is what keeps the hero's auto-expand armed by FAB-initiated runs only.
 */
class DashboardMainActionDialogTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun setDialog(onConfirmMainAction: (BottomBarState.Action) -> Unit) {
        composeRule.setContent {
            PreviewWrapper {
                DashboardEventDialogs(
                    state = DashboardDialogState.MainActionDelete(BottomBarState.Action.DELETE),
                    onDismiss = {},
                    onConfirmCorpseFinder = {},
                    onShowCorpseFinder = {},
                    onConfirmSystemCleaner = {},
                    onShowSystemCleaner = {},
                    onConfirmAppCleaner = {},
                    onShowAppCleaner = {},
                    onConfirmDeduplicator = {},
                    onShowDeduplicator = {},
                    onPreviewDeduplicator = {},
                    onStopShortRecording = {},
                    onConfirmMainAction = onConfirmMainAction,
                )
            }
        }
    }

    @Test
    fun `merely opening the dialog does not run the action`() {
        var confirmed: BottomBarState.Action? = null
        setDialog { confirmed = it }

        composeRule.onNodeWithText(context.getString(CommonR.string.general_delete_confirmation_title))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertNull(confirmed) }
    }

    @Test
    fun `confirming the dialog runs the main action`() {
        var confirmed: BottomBarState.Action? = null
        setDialog { confirmed = it }

        composeRule.onNodeWithText(context.getString(CommonR.string.general_delete_action)).performClick()

        composeRule.runOnIdle { assertEquals(BottomBarState.Action.DELETE, confirmed) }
    }

    @Test
    fun `cancelling the dialog does not run the main action`() {
        var confirmed: BottomBarState.Action? = null
        setDialog { confirmed = it }

        composeRule.onNodeWithText(context.getString(CommonR.string.general_cancel_action)).performClick()

        composeRule.runOnIdle { assertNull(confirmed) }
    }
}
