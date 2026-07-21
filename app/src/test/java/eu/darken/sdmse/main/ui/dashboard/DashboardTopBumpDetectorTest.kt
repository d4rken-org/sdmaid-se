package eu.darken.sdmse.main.ui.dashboard

import io.kotest.matchers.shouldBe
import org.junit.Test

class DashboardTopBumpDetectorTest {

    @Test
    fun `returning to the top after a large scroll emits one bump`() {
        val detector = DashboardTopBumpDetector(largeScrollDistancePx = 100)

        detector.onScroll(0, 120, deltaY = -120f) shouldBe false
        detector.onScroll(0, 20, deltaY = 100f) shouldBe false
        detector.onScroll(0, 0, deltaY = 20f) shouldBe true
        detector.onScroll(0, 0, deltaY = 8f) shouldBe false
    }

    @Test
    fun `fast return emits a bump after a shorter scroll`() {
        val detector = DashboardTopBumpDetector(largeScrollDistancePx = 100)

        detector.onScroll(0, 30, deltaY = -30f) shouldBe false
        detector.onScroll(0, 0, deltaY = 30f, isFastReturn = true) shouldBe true
    }

    @Test
    fun `pulling at the resting top does not bump even when fast`() {
        val detector = DashboardTopBumpDetector(largeScrollDistancePx = 100)

        detector.onScroll(0, 0, deltaY = 20f, isFastReturn = true) shouldBe false
    }

    @Test
    fun `small leisurely scroll does not bump`() {
        val detector = DashboardTopBumpDetector(largeScrollDistancePx = 100)

        detector.onScroll(0, 60, deltaY = -60f) shouldBe false
        detector.onScroll(0, 0, deltaY = 10f) shouldBe false
    }

    @Test
    fun `programmatic motion clears a pending bump`() {
        val detector = DashboardTopBumpDetector(largeScrollDistancePx = 100)

        detector.onScroll(1, 0, deltaY = -40f) shouldBe false
        detector.onNonUserScroll()
        detector.onScroll(0, 0, deltaY = 40f) shouldBe false
    }
}
