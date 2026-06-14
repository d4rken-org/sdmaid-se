package eu.darken.sdmse.systemcleaner.ui.list.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.systemcleaner.R

object SystemCleanerListTour {

    val id: TourId = TourId("tour.systemcleaner.list")

    const val FILTER_ROW_TARGET = "systemcleaner.filterRow"

    /**
     * A centerless intro explains the non-obvious filter concept (ready-made rules), then the first
     * filter row is anchored to teach the "open details to see matching files" affordance. A
     * populated screen always has at least one row, so the anchored step won't grace-skip.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                title = R.string.tour_systemcleaner_intro_title.toCaString(),
                body = R.string.tour_systemcleaner_intro_body.toCaString(),
            ),
            TourStep(
                stepId = "filter",
                targetId = FILTER_ROW_TARGET,
                title = R.string.tour_systemcleaner_filter_title.toCaString(),
                body = R.string.tour_systemcleaner_filter_body.toCaString(),
            ),
        ),
    )
}
