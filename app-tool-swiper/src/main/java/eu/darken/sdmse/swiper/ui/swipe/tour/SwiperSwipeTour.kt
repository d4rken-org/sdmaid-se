package eu.darken.sdmse.swiper.ui.swipe.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.GuidedTour
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.swiper.R

object SwiperSwipeTour : GuidedTour {

    override val id: TourId = TourId("tour.swiper.swipe")

    const val OPEN_IN_TARGET = "swiper.openIn"
    const val FULLSCREEN_PREVIEW_TARGET = "swiper.fullscreenPreview"
    const val ACTIONS_TARGET = "swiper.actions"
    const val REVIEW_TARGET = "swiper.review"

    /**
     * This tour fully owns Swiper onboarding (it replaced the old first-run gesture overlay).
     *
     * Step 1 is a centerless intro that teaches the swipe-to-decide gestures. The copy is kept
     * direction-neutral (sideways = keep/delete, up = skip, down = undo) rather than naming
     * left/right, because [SwiperSettings.swapSwipeDirections] can flip the sides — the labeled
     * action-bar buttons (anchored two steps later) carry the which-side specifics.
     *
     * The remaining steps anchor on-screen affordances that are all present once a swipe session
     * is loaded (front-card corner buttons, action bar, review badge), so none risk grace-skipping.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "gestures",
                targetId = null,
                body = R.string.tour_swiper_gestures_body.toCaString(),
            ),
            TourStep(
                stepId = "openIn",
                targetId = OPEN_IN_TARGET,
                body = R.string.tour_swiper_openin_body.toCaString(),
            ),
            TourStep(
                stepId = "preview",
                targetId = FULLSCREEN_PREVIEW_TARGET,
                body = R.string.tour_swiper_preview_body.toCaString(),
            ),
            TourStep(
                stepId = "actions",
                targetId = ACTIONS_TARGET,
                body = R.string.tour_swiper_actions_body.toCaString(),
            ),
            TourStep(
                stepId = "review",
                targetId = REVIEW_TARGET,
                body = R.string.tour_swiper_review_body.toCaString(),
            ),
        ),
    )
}
