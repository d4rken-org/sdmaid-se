package eu.darken.sdmse.main.ui.dashboard.bottom

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Guards the large-font fix for the dashboard hero card: its height must grow with the user's font
 * scale (so the caption + two-line tap-hint don't clip), clamped so it can't grow unbounded, and
 * never shrink below the font-scale-1.0 worst-case design height.
 */
class DashboardHeroHeightScalingTest : BaseComposeRobolectricTest() {

    private data class Heights(val card: Dp, val dock: Dp)

    /**
     * Reads the @Composable height getters under each [fontScale] in a single composition —
     * [androidx.compose.ui.test.junit4.ComposeContentTestRule.setContent] may only be called once
     * per test, so every scale a test needs is captured here together.
     */
    private fun heightsAt(vararg fontScales: Float): Map<Float, Heights> {
        val out = linkedMapOf<Float, Heights>()
        composeRule.setContent {
            fontScales.forEach { scale ->
                CompositionLocalProvider(LocalDensity provides Density(density = 2.75f, fontScale = scale)) {
                    out[scale] = Heights(card = dashboardHeroCardHeight, dock = dashboardDockHeightWithHero)
                }
            }
        }
        composeRule.waitForIdle()
        return out
    }

    private val baselineCard = DASHBOARD_HERO_CONTENT_HEIGHT + DASHBOARD_CUTOUT_DEPTH

    @Test
    fun `card height tracks font scale`() {
        val h = heightsAt(1.0f, 1.5f)
        h.getValue(1.0f).card shouldBe baselineCard
        h.getValue(1.5f).card shouldBe DASHBOARD_HERO_CONTENT_HEIGHT * 1.5f + DASHBOARD_CUTOUT_DEPTH
    }

    @Test
    fun `growth is clamped at 2x`() {
        val h = heightsAt(2.0f, 2.5f)
        h.getValue(2.0f).card shouldBe DASHBOARD_HERO_CONTENT_HEIGHT * 2.0f + DASHBOARD_CUTOUT_DEPTH
        // Past the 2.0 ceiling the card stops growing instead of eating the screen.
        h.getValue(2.5f).card shouldBe h.getValue(2.0f).card
    }

    @Test
    fun `never shrinks below the font-scale-1 baseline`() {
        heightsAt(0.85f).getValue(0.85f).card shouldBe baselineCard
    }

    @Test
    fun `dock reservation is bar plus gap plus the scaled card`() {
        val h = heightsAt(1.5f).getValue(1.5f)
        h.dock shouldBe DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP + h.card
    }
}
