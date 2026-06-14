package eu.darken.sdmse.analyzer.ui.storage.device.tour

import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep

object AnalyzerStorageTour {

    val id: TourId = TourId("tour.analyzer.storage")

    const val STORAGE_CARD_TARGET = "analyzer.storageCard"

    /**
     * The leading "intro" step is centerless — it frames what the Analyzer is (a storage breakdown,
     * not a one-tap cleaner) before the second step anchors the first storage card to teach the
     * drill-down. The storage card is the only anchored target; a first-time scan always produces at
     * least one storage row, so it's a safe always-present step-1-after-intro anchor.
     */
    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                title = R.string.tour_analyzer_intro_title.toCaString(),
                body = R.string.tour_analyzer_intro_body.toCaString(),
            ),
            TourStep(
                stepId = "storage",
                targetId = STORAGE_CARD_TARGET,
                title = R.string.tour_analyzer_storage_title.toCaString(),
                body = R.string.tour_analyzer_storage_body.toCaString(),
            ),
        ),
    )
}
