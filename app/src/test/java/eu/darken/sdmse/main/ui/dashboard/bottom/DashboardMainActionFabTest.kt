package eu.darken.sdmse.main.ui.dashboard.bottom

import android.content.Context
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

/**
 * The main-action FAB is the app's primary destructive control and sits bottom-centre, right where
 * the home gesture handle is. These pin down that it is actually hittable: a target wider than its
 * visual, and no press length that silently drops the action.
 */
class DashboardMainActionFabTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val deleteLabel: String get() = context.getString(CommonR.string.general_delete_action)

    private fun setDock(onMainAction: () -> Unit) {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = BottomBarState(
                        isReady = true,
                        actionState = BottomBarState.Action.DELETE,
                        activeTasks = 0,
                        queuedTasks = 0,
                        heroSummary = null,
                        upgradeInfo = null,
                    ),
                    isVisible = true,
                    heroVisible = false,
                    onMainAction = onMainAction,
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
    }

    /**
     * `clickable` merges its descendants, so the node found by the icon's description IS the
     * clickable — asserting its bounds asserts the hit rect. Fails both if the slack is dropped and
     * if the click is moved back onto the visible [DASHBOARD_FAB_SIZE] surface.
     */
    @Test
    fun `the hit rect is larger than the visual FAB`() {
        setDock {}

        val hitRect = composeRule.onNodeWithContentDescription(deleteLabel)
            .getBoundsInRoot()
        val width = hitRect.right - hitRect.left
        val height = hitRect.bottom - hitRect.top

        assertTrue(
            "FAB hit rect $width x $height must exceed the $DASHBOARD_FAB_SIZE visual",
            width > DASHBOARD_FAB_SIZE && height > DASHBOARD_FAB_SIZE,
        )
    }

    @Test
    fun `a long press still triggers the action`() {
        var invoked = 0
        setDock { invoked++ }

        composeRule.onNodeWithContentDescription(deleteLabel)
            .performTouchInput { longClick() }

        composeRule.runOnIdle { assertEquals(1, invoked) }
    }
}
