package eu.darken.sdmse.deduplicator.ui.details.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.deduplicator.R

object DeduplicatorDetailsTour {

    val id: TourId = TourId("tour.deduplicator.details")

    const val CLUSTER_HEADER_TARGET = "deduplicator.details.clusterHeader"
    const val ROW_DELETE_MARK_TARGET = "deduplicator.details.rowDeleteMark"

    fun definition(
        prepareDeleteMark: (suspend () -> Unit)? = null,
    ): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "cluster",
                targetId = CLUSTER_HEADER_TARGET,
                title = R.string.tour_deduplicator_details_cluster_title.toCaString(),
                body = R.string.tour_deduplicator_details_cluster_body.toCaString(),
            ),
            TourStep(
                stepId = "deleteMark",
                targetId = ROW_DELETE_MARK_TARGET,
                title = R.string.tour_deduplicator_details_delete_mark_title.toCaString(),
                body = R.string.tour_deduplicator_details_delete_mark_body.toCaString(),
                prepareTarget = prepareDeleteMark,
            ),
        ),
    )
}
