package eu.darken.sdmse.common.upgrade.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SponsorReturnTrackerTest {

    @Test
    fun `resume only counts after background transition`() {
        val tracker = SponsorReturnTracker()

        tracker.consumeResumeReturn() shouldBe false

        tracker.onStop()

        tracker.consumeResumeReturn() shouldBe true
        tracker.consumeResumeReturn() shouldBe false
    }
}
