package eu.darken.sdmse.common.upgrade.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorReturnTrackerTest {

    @Test
    fun `resume only counts after background transition`() {
        val tracker = SponsorReturnTracker()

        assertFalse(tracker.consumeResumeReturn())

        tracker.onStop()

        assertTrue(tracker.consumeResumeReturn())
        assertFalse(tracker.consumeResumeReturn())
    }
}
