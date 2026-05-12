package eu.darken.sdmse.appcontrol.ui.list.tour

import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep

object AppControlListTour {

    val id: TourId = TourId("tour.appcontrol.list")

    const val SEARCH_TARGET = "appcontrol.search"
    const val FILTER_TARGET = "appcontrol.filter"
    const val SORT_TARGET = "appcontrol.sort"
    const val APP_ROW_TARGET = "appcontrol.appRow"

    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "search",
                targetId = SEARCH_TARGET,
                title = R.string.tour_appcontrol_search_title.toCaString(),
                body = R.string.tour_appcontrol_search_body.toCaString(),
            ),
            TourStep(
                stepId = "filter",
                targetId = FILTER_TARGET,
                title = R.string.tour_appcontrol_filter_title.toCaString(),
                body = R.string.tour_appcontrol_filter_body.toCaString(),
            ),
            TourStep(
                stepId = "sort",
                targetId = SORT_TARGET,
                title = R.string.tour_appcontrol_sort_title.toCaString(),
                body = R.string.tour_appcontrol_sort_body.toCaString(),
            ),
            TourStep(
                stepId = "appRow",
                targetId = APP_ROW_TARGET,
                title = R.string.tour_appcontrol_app_row_title.toCaString(),
                body = R.string.tour_appcontrol_app_row_body.toCaString(),
            ),
        ),
    )
}
