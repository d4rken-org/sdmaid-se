package eu.darken.sdmse.common.progress

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ProgressCountTest : BaseTest() {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `percent rounds up`() {
        // What the cache clearing overlay shows while stepping through three apps. Rounding up is
        // why users report "0, then about 30, then about 60" rather than 0/33/66.
        Progress.Count.Percent(0, 3).displayValue(context) shouldBe "0%"
        Progress.Count.Percent(1, 3).displayValue(context) shouldBe "34%"
        Progress.Count.Percent(2, 3).displayValue(context) shouldBe "67%"
        Progress.Count.Percent(3, 3).displayValue(context) shouldBe "100%"
    }

    @Test
    fun `percent handles an empty range`() {
        Progress.Count.Percent(0, 0).displayValue(context) shouldBe "NaN"
    }

    @Test
    fun `counter shows raw values`() {
        Progress.Count.Counter(1, 3).displayValue(context) shouldBe "1/3"
    }

    @Test
    fun `indeterminate and none have nothing to show`() {
        Progress.Count.Indeterminate().displayValue(context) shouldBe ""
        Progress.Count.None().displayValue(context) shouldBe null
    }

    @Test
    fun `counter and percent drive a determinate ring`() {
        Progress.Count.Counter(1, 4).determinateFraction() shouldBe 0.25f
        Progress.Count.Percent(50, 100).determinateFraction() shouldBe 0.5f
    }

    @Test
    fun `determinate fraction clamps to the ring range`() {
        Progress.Count.Counter(9, 4).determinateFraction() shouldBe 1f
        Progress.Count.Counter(-1, 4).determinateFraction() shouldBe 0f
    }

    @Test
    fun `an unknown total makes the ring spin`() {
        // Percent(0) / Counter(0) is what a producer publishes before it knows the item count.
        Progress.Count.Counter(0, 0).determinateFraction() shouldBe null
        Progress.Count.Percent(0, 0).determinateFraction() shouldBe null
    }

    @Test
    fun `size, indeterminate, none and null make the ring spin`() {
        // Size is deliberately excluded: the overlay has never rendered a determinate arc for it.
        Progress.Count.Size(1, 2).determinateFraction() shouldBe null
        Progress.Count.Indeterminate().determinateFraction() shouldBe null
        Progress.Count.None().determinateFraction() shouldBe null
        (null as Progress.Count?).determinateFraction() shouldBe null
    }
}
