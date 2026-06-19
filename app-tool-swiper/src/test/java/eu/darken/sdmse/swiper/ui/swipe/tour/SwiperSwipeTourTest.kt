package eu.darken.sdmse.swiper.ui.swipe.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SwiperSwipeTourTest : BaseTest() {

    @Test
    fun `definition opens with the centerless gesture intro then anchors the affordances`() {
        val def = SwiperSwipeTour.definition()
        def.id shouldBe TourId("tour.swiper.swipe")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("gestures", "openIn", "preview", "actions", "review")
        def.steps.map { it.targetId } shouldBe listOf(
            null,
            SwiperSwipeTour.OPEN_IN_TARGET,
            SwiperSwipeTour.FULLSCREEN_PREVIEW_TARGET,
            SwiperSwipeTour.ACTIONS_TARGET,
            SwiperSwipeTour.REVIEW_TARGET,
        )
    }

    @Test
    fun `only the leading gesture step is centerless — the rest are anchored`() {
        val steps = SwiperSwipeTour.definition().steps
        // The gesture intro replaced the old overlay and needs no anchor.
        steps.first().targetId shouldBe null
        // Every following affordance is present once a session loads, so none grace-skips.
        steps.drop(1).forEach { it.targetId shouldNotBe null }
    }
}
