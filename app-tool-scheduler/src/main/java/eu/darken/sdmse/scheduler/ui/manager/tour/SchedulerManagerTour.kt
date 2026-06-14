package eu.darken.sdmse.scheduler.ui.manager.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.scheduler.R

object SchedulerManagerTour {

    val id: TourId = TourId("tour.scheduler.manager")

    const val ADD_TARGET = "scheduler.add"

    /**
     * A first-time user's manager screen is empty except the add FAB (no schedule rows, the battery
     * hint is conditional), so the only anchored step is the FAB — anchoring a schedule row would
     * grace-skip for exactly the audience this tour targets. The centerless intro carries the
     * "what scheduling does" explanation.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                title = R.string.tour_scheduler_intro_title.toCaString(),
                body = R.string.tour_scheduler_intro_body.toCaString(),
            ),
            TourStep(
                stepId = "create",
                targetId = ADD_TARGET,
                title = R.string.tour_scheduler_create_title.toCaString(),
                body = R.string.tour_scheduler_create_body.toCaString(),
            ),
        ),
    )
}
