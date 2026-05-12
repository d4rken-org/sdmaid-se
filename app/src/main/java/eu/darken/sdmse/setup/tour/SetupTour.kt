package eu.darken.sdmse.setup.tour

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep

object SetupTour {

    val id: TourId = TourId("tour.setup")

    const val HEADER_TARGET = "setup.header"

    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = HEADER_TARGET,
                title = R.string.tour_setup_intro_title.toCaString(),
                body = R.string.tour_setup_intro_body.toCaString(),
            ),
        ),
    )
}
