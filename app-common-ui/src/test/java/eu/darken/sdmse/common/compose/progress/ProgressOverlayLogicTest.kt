package eu.darken.sdmse.common.compose.progress

import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.determinateFraction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ProgressOverlayLogicTest : BaseTest() {

    @Test
    fun `a determinate sub count owns the hero number`() {
        // Video transcode: the item counter barely moves, the per-file percentage is what the
        // user watches.
        heroCount(
            count = Progress.Count.Counter(1, 2),
            subCount = Progress.Count.Percent(42, 100),
        ) shouldBe Progress.Count.Percent(42, 100)
    }

    @Test
    fun `no sub count leaves the hero number on the overall count`() {
        val count = Progress.Count.Counter(1, 2)
        heroCount(count = count, subCount = null) shouldBe count
    }

    @Test
    fun `an indeterminate sub count leaves the hero number on the overall count`() {
        // Images, and videos before Media3's first available progress poll.
        val count = Progress.Count.Counter(1, 2)
        heroCount(count = count, subCount = Progress.Count.Indeterminate()) shouldBe count
    }

    @Test
    fun `a sub count without a total leaves the hero number on the overall count`() {
        val count = Progress.Count.Counter(1, 2)
        heroCount(count = count, subCount = Progress.Count.Percent(0, 0)) shouldBe count
    }

    @Test
    fun `an indeterminate sub count spins the inner ring`() {
        // What makes the inner ring spin instead of showing a frozen arc.
        Progress.Count.Indeterminate().determinateFraction() shouldBe null
        Progress.Count.Percent(42, 100).determinateFraction() shouldBe 0.42f
    }
}
