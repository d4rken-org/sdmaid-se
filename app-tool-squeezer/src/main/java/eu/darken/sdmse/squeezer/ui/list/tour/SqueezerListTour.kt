package eu.darken.sdmse.squeezer.ui.list.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.squeezer.R

object SqueezerListTour {

    val id: TourId = TourId("tour.squeezer.list")

    const val COMPRESS_ALL_TARGET = "squeezer.compressAll"

    /**
     * A centerless intro frames the (non-obvious, lossy) "re-compress media to save space" idea,
     * then the compress-all FAB is anchored. The per-item tap action is described in copy rather
     * than anchored, to avoid threading targets through both the grid and linear layouts; the
     * quality slider lives in a modal dialog and is out of scope for anchoring.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                title = R.string.tour_squeezer_intro_title.toCaString(),
                body = R.string.tour_squeezer_intro_body.toCaString(),
            ),
            TourStep(
                stepId = "compress",
                targetId = COMPRESS_ALL_TARGET,
                title = R.string.tour_squeezer_compress_title.toCaString(),
                body = R.string.tour_squeezer_compress_body.toCaString(),
            ),
        ),
    )
}
