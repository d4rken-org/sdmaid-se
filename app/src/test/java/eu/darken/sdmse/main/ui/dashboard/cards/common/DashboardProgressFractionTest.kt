package eu.darken.sdmse.main.ui.dashboard.cards.common

import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.determinateFraction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DashboardProgressFractionTest : BaseTest() {

    @Test
    fun `size stays determinate`() {
        // Regression guard: the shared overlay helper excludes Size, the card must not — every
        // Analyzer / CorpseFinder deletion card would spin forever.
        dashboardFraction(Progress.Count.Size(1, 2), null) shouldBe 0.5f
    }

    @Test
    fun `without a sub count the fraction is unchanged`() {
        dashboardFraction(Progress.Count.Counter(1, 3), null) shouldBe (1f / 3f)
        dashboardFraction(Progress.Count.Percent(42, 100), null) shouldBe 0.42f
    }

    @Test
    fun `an unknown total has no fraction`() {
        dashboardFraction(Progress.Count.Counter(0, 0), null) shouldBe null
        dashboardFraction(Progress.Count.Counter(0, 0), Progress.Count.Percent(50, 100)) shouldBe null
        dashboardFraction(Progress.Count.Indeterminate(), null) shouldBe null
        dashboardFraction(Progress.Count.None(), null) shouldBe null
    }

    @Test
    fun `a determinate sub count blends into the item fraction`() {
        // Item 2 of 4 is half done => 1.5 of 4.
        dashboardFraction(Progress.Count.Counter(1, 4), Progress.Count.Percent(50, 100)) shouldBe 0.375f
    }

    @Test
    fun `an indeterminate sub count does not blend`() {
        dashboardFraction(Progress.Count.Counter(1, 4), Progress.Count.Indeterminate()) shouldBe 0.25f
    }

    @Test
    fun `the blended fraction stays inside the ring range`() {
        dashboardFraction(Progress.Count.Counter(4, 4), Progress.Count.Percent(100, 100)) shouldBe 1f
        dashboardFraction(Progress.Count.Counter(-1, 4), Progress.Count.Percent(0, 100)) shouldBe 0f
    }

    @Test
    fun `a known zero percent sub count is determinate`() {
        // Media3's first available poll reports 0%. The blended fraction is exactly 0f, but it is a
        // real measurement, so the card must not fall back to the "no information" spinner.
        val fraction = dashboardFraction(Progress.Count.Counter(0, 1), Progress.Count.Percent(0, 100))
        fraction shouldBe 0f
        Progress.Count.Percent(0, 100).determinateFraction() shouldBe 0f
    }

    @Test
    fun `an indeterminate sub count keeps the legacy spinner`() {
        dashboardFraction(Progress.Count.Counter(0, 1), Progress.Count.Indeterminate()) shouldBe 0f
        Progress.Count.Indeterminate().determinateFraction() shouldBe null
    }

    @Test
    fun `the percentage does not overshoot the real value`() {
        // Via the float fraction these two came out as 31% and 61%.
        dashboardPercent(Progress.Count.Counter(0, 1), Progress.Count.Percent(30, 100)) shouldBe 30
        dashboardPercent(Progress.Count.Counter(0, 1), Progress.Count.Percent(60, 100)) shouldBe 60
    }

    @Test
    fun `the percentage rounds a blended value up`() {
        // Item 2 of 4 is half done => 37.5%.
        dashboardPercent(Progress.Count.Counter(1, 4), Progress.Count.Percent(50, 100)) shouldBe 38
    }

    @Test
    fun `the percentage ignores an indeterminate sub count`() {
        // Rounded up, matching Percent.displayValue.
        dashboardPercent(Progress.Count.Counter(1, 3), Progress.Count.Indeterminate()) shouldBe 34
        dashboardPercent(Progress.Count.Counter(1, 3), null) shouldBe 34
    }

    @Test
    fun `the percentage stays inside 0 and 100`() {
        dashboardPercent(Progress.Count.Counter(0, 0), null) shouldBe 0
        dashboardPercent(Progress.Count.Counter(0, 0), Progress.Count.Percent(50, 100)) shouldBe 0
        dashboardPercent(Progress.Count.Counter(-1, 4), Progress.Count.Percent(0, 100)) shouldBe 0
        dashboardPercent(Progress.Count.Counter(9, 4), Progress.Count.Percent(200, 100)) shouldBe 100
    }
}
