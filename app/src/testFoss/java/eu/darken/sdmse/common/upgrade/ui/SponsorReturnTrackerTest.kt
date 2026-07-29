package eu.darken.sdmse.common.upgrade.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SponsorReturnTrackerTest : BaseTest() {

    @Test
    fun `resume only counts after background transition`() {
        val tracker = SponsorReturnTracker()

        tracker.consumeResumeReturn() shouldBe false

        tracker.onStop()

        tracker.consumeResumeReturn() shouldBe true
        tracker.consumeResumeReturn() shouldBe false
    }

    /**
     * The process can be killed while the sponsor page is in front, i.e. between the launch and the
     * return. The recomposed screen gets a brand-new tracker that never saw the ON_STOP, so gating
     * on in-memory state alone would swallow that first return for good. The handle-backed pending
     * launch is the authority and seeds the tracker instead.
     */
    @Test
    fun `a tracker seeded from a pending launch counts the first resume`() {
        val tracker = SponsorReturnTracker(wentToBackground = true)

        tracker.consumeResumeReturn() shouldBe true
        tracker.consumeResumeReturn() shouldBe false
    }
}
