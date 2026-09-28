package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.darken.sdmse.main.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import testhelper.BaseAppFlowTest
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.corpsefinder.R as CorpseFinderR

@RunWith(AndroidJUnit4::class)
class DashboardMainActionFlowTest : BaseAppFlowTest() {

    /** Scrolling the list down hides the dock and scrolling back up shows it; its scan button must still take taps. */
    @Test
    fun scanStartsAfterTheDockWasHiddenAndShownByScrolling() {
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            // Onboarding ends on Setup, pushed over the dashboard.
            val closeSetup = hasContentDescription(str(CommonR.string.general_close_action))
            awaitNode(closeSetup)
            composeRule.onNode(closeSetup).performClick()

            val scan = hasContentDescription(str(CommonR.string.general_scan_action))
            awaitNode(scan)
            // The hidden dock leaves the accessibility tree, so the scan button's absence shows it hid.
            swipeList(SWIPES, up = true)
            awaitGone(scan)
            swipeList(SWIPES, up = false)
            awaitNode(scan)

            composeRule.onNode(scan).performClick()
            awaitListItem(corpseFinderResult())
        }
    }

    // The default swipes start at the list's bottom edge, which is inside the scan button's touch area.
    private fun swipeList(times: Int, up: Boolean) = repeat(times) {
        composeRule.onNode(hasScrollToNodeAction()).performTouchInput {
            val low = Offset(width * 0.2f, height * 0.8f)
            val high = Offset(width * 0.2f, height * 0.2f)
            if (up) swipe(low, high, SWIPE_MS) else swipe(high, low, SWIPE_MS)
        }
        composeRule.waitForIdle()
    }

    private fun corpseFinderResult(): SemanticsMatcher {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val results = (0..100).map {
            resources.getQuantityString(CorpseFinderR.plurals.corpsefinder_result_x_corpses_found, it, it)
        }.toSet()
        return SemanticsMatcher("CorpseFinder scan result") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text in results }
        }
    }

    companion object {
        private const val SWIPES = 6
        private const val SWIPE_MS = 300L
    }
}
