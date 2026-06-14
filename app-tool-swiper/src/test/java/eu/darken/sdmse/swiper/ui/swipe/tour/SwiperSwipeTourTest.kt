package eu.darken.sdmse.swiper.ui.swipe.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SwiperSwipeTourTest : BaseTest() {

    @Test
    fun `definition anchors the action bar then the review badge`() {
        val def = SwiperSwipeTour.definition()
        def.id shouldBe TourId("tour.swiper.swipe")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("actions", "review")
        def.steps.map { it.targetId } shouldBe listOf(
            SwiperSwipeTour.ACTIONS_TARGET,
            SwiperSwipeTour.REVIEW_TARGET,
        )
    }

    @Test
    fun `every step is anchored — no centerless steps`() {
        // Both targets are always present once a session loads, so neither step grace-skips.
        SwiperSwipeTour.definition().steps.forEach { it.targetId shouldNotBe null }
    }
}
