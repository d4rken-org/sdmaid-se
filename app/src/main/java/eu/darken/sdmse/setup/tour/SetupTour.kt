package eu.darken.sdmse.setup.tour

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.GuidedTour
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep

object SetupTour : GuidedTour {

    override val id: TourId = TourId("tour.setup")

    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                body = R.string.tour_setup_intro_body.toCaString(),
            ),
        ),
    )
}
