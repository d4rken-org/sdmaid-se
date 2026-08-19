package eu.darken.sdmse.main.ui.dashboard.bottom

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The bar's task counters used to be one `"$active\n$queued"` string capped at two lines: whenever
 * the first line wrapped it ate the second line's budget and the queued count disappeared, and in
 * locales that put the number last ("Задач в очереди: %d") a wrap dropped the digit alone
 * (issue #2698).
 *
 * Robolectric's text metrics are stubs (roughly 1dp per character, never wrapping), so this can't
 * assert the wrap itself, and semantics keep the full string even when its glyphs are clipped — a
 * passing run here is not evidence that the digit is on screen. What it does pin down is the
 * structural property the fix rests on: each count is its own node, so neither can consume the
 * other's line budget. Re-joining them into a single string fails both assertions, because that
 * node's text is the concatenation and matches neither count exactly.
 *
 * Read against the unmerged tree: the counters merge into one TalkBack stop on purpose, and the
 * merged node would match both counts and prove nothing about them being separate.
 */
class DashboardBarTaskCountsTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun setDock(activeTasks: Int, queuedTasks: Int) {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = BottomBarState(
                        isReady = true,
                        actionState = BottomBarState.Action.WORKING,
                        activeTasks = activeTasks,
                        queuedTasks = queuedTasks,
                        heroSummary = null,
                        upgradeInfo = null,
                    ),
                    isVisible = true,
                    heroVisible = false,
                    onMainAction = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
    }

    @Test
    fun `each task count is its own node`() {
        setDock(activeTasks = 2, queuedTasks = 1)

        val active = context.getQuantityString2(R.plurals.tasks_activity_active_notification_message, 2)
        val queued = context.getQuantityString2(R.plurals.tasks_activity_queued_notification_message, 1)

        composeRule.onNodeWithText(active, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(queued, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * The queued line renders even at zero: the bar shows up as soon as *either* counter is
     * non-zero, and "no tasks waiting" is exactly the state the missing line was mistaken for.
     */
    @Test
    fun `the queued count renders while only active tasks run`() {
        setDock(activeTasks = 1, queuedTasks = 0)

        val queued = context.getQuantityString2(R.plurals.tasks_activity_queued_notification_message, 0)

        composeRule.onNodeWithText(queued, useUnmergedTree = true).assertIsDisplayed()
    }
}
