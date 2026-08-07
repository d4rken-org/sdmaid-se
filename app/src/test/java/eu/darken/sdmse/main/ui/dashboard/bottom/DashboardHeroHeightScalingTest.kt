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

    /** Both card variants at one font scale: without the nested upgrade block, and with it. */
    private data class ScaleHeights(val flat: Heights, val withBlock: Heights)

    /**
     * Reads the @Composable height functions under each [fontScale] in a single composition —
     * [androidx.compose.ui.test.junit4.ComposeContentTestRule.setContent] may only be called once
     * per test, so every scale a test needs is captured here together.
     */
    private fun heightsAt(vararg fontScales: Float): Map<Float, ScaleHeights> {
        val out = linkedMapOf<Float, ScaleHeights>()
        composeRule.setContent {
            fontScales.forEach { scale ->
                CompositionLocalProvider(LocalDensity provides Density(density = 2.75f, fontScale = scale)) {
                    out[scale] = ScaleHeights(
                        flat = Heights(
                            card = dashboardHeroCardHeight(withUpgradeBlock = false),
                            dock = dashboardDockHeightWithHero(withUpgradeBlock = false),
                        ),
                        withBlock = Heights(
                            card = dashboardHeroCardHeight(withUpgradeBlock = true),
                            dock = dashboardDockHeightWithHero(withUpgradeBlock = true),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return out
    }

    private val baselineCard = DASHBOARD_HERO_CONTENT_HEIGHT + DASHBOARD_CUTOUT_DEPTH
    private val baselineBlockCard = DASHBOARD_HERO_CONTENT_HEIGHT_WITH_BLOCK + DASHBOARD_CUTOUT_DEPTH

    @Test
    fun `card height tracks font scale`() {
        val h = heightsAt(1.0f, 1.5f)
        h.getValue(1.0f).flat.card shouldBe baselineCard
        h.getValue(1.5f).flat.card shouldBe DASHBOARD_HERO_CONTENT_HEIGHT * 1.5f + DASHBOARD_CUTOUT_DEPTH
    }

    @Test
    fun `growth is clamped at 2x`() {
        val h = heightsAt(2.0f, 2.5f)
        h.getValue(2.0f).flat.card shouldBe DASHBOARD_HERO_CONTENT_HEIGHT * 2.0f + DASHBOARD_CUTOUT_DEPTH
        // Past the 2.0 ceiling the card stops growing instead of eating the screen.
        h.getValue(2.5f).flat.card shouldBe h.getValue(2.0f).flat.card
    }

    @Test
    fun `never shrinks below the font-scale-1 baseline`() {
        heightsAt(0.85f).getValue(0.85f).flat.card shouldBe baselineCard
    }

    @Test
    fun `dock reservation is bar plus gap plus the scaled card`() {
        val h = heightsAt(1.5f).getValue(1.5f).flat
        h.dock shouldBe DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP + h.card
    }

    @Test
    fun `the upgrade block gets the taller base, scaled the same way`() {
        // Only the base height differs; the font-scale multiplier and the fixed cradle notch are the
        // same, so the two variants can never drift apart in how they respond to font size.
        val h = heightsAt(1.0f, 1.5f)
        h.getValue(1.0f).withBlock.card shouldBe baselineBlockCard
        h.getValue(1.5f).withBlock.card shouldBe DASHBOARD_HERO_CONTENT_HEIGHT_WITH_BLOCK * 1.5f + DASHBOARD_CUTOUT_DEPTH
    }

    @Test
    fun `the block variant is taller than the flat one at every scale`() {
        val h = heightsAt(1.0f, 2.0f)
        h.values.forEach { (flat, withBlock) ->
            (withBlock.card > flat.card) shouldBe true
            withBlock.dock shouldBe DASHBOARD_BAR_HEIGHT + DASHBOARD_HERO_BAR_GAP + withBlock.card
        }
    }

    @Test
    fun `the block variant is also clamped at 2x`() {
        val h = heightsAt(2.0f, 2.5f)
        h.getValue(2.5f).withBlock.card shouldBe h.getValue(2.0f).withBlock.card
    }
}
