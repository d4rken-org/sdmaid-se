package eu.darken.sdmse.corpsefinder.ui.list.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.corpsefinder.R

object CorpseFinderListTour {

    val id: TourId = TourId("tour.corpsefinder.list")

    const val CORPSE_ROW_TARGET = "corpsefinder.corpseRow"

    /**
     * CorpseFinder deletes "orphaned" data, which can read as scary — so the centerless intro
     * leads with reassurance (it belongs to apps you already uninstalled, safe to remove), then the
     * first corpse row is anchored to explain the per-item markers. A populated result always has at
     * least one row, so the anchored step won't grace-skip.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                title = R.string.tour_corpsefinder_intro_title.toCaString(),
                body = R.string.tour_corpsefinder_intro_body.toCaString(),
            ),
            TourStep(
                stepId = "row",
                targetId = CORPSE_ROW_TARGET,
                title = R.string.tour_corpsefinder_row_title.toCaString(),
                body = R.string.tour_corpsefinder_row_body.toCaString(),
            ),
        ),
    )
}
