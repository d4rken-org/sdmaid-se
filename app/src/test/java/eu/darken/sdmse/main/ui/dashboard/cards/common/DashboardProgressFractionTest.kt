package eu.darken.sdmse.main.ui.dashboard.cards.common

import eu.darken.sdmse.common.progress.Progress
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
}
