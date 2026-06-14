package eu.darken.sdmse.swiper.ui.swipe.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.swiper.R

object SwiperSwipeTour {

    val id: TourId = TourId("tour.swiper.swipe")

    const val ACTIONS_TARGET = "swiper.actions"
    const val REVIEW_TARGET = "swiper.review"

    /**
     * Complements the first-run gesture overlay, which already teaches swipe-to-decide. This tour
     * only covers what that overlay omits: the action-bar buttons (notably long-press Skip =
     * exclude) and that decisions are staged until applied from Review. The screen gates the start
     * on the gesture overlay being dismissed so the two onboarding surfaces never overlap.
     *
     * Both anchored targets (action bar, review badge) are always present once a swipe session is
     * loaded, so neither step risks grace-skipping.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "actions",
                targetId = ACTIONS_TARGET,
                title = R.string.tour_swiper_actions_title.toCaString(),
                body = R.string.tour_swiper_actions_body.toCaString(),
            ),
            TourStep(
                stepId = "review",
                targetId = REVIEW_TARGET,
                title = R.string.tour_swiper_review_title.toCaString(),
                body = R.string.tour_swiper_review_body.toCaString(),
            ),
        ),
    )
}
