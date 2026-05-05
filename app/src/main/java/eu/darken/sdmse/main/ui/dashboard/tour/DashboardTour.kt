package eu.darken.sdmse.main.ui.dashboard.tour

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep

object DashboardTour {

    val id: TourId = TourId("tour.dashboard")

    const val SETUP_TARGET = "dashboard.setup"
    const val TOOLS_TARGET = "dashboard.firstTool"
    const val MAIN_ACTION_TARGET = "dashboard.mainAction"
    const val MANUAL_TOOL_TARGET = "dashboard.manualTool"
    const val SETTINGS_TARGET = "dashboard.settings"

    /**
     * Builds the tour definition. The screen passes a [prepareManualTool] hook to scroll the
     * dashboard's lazy grid to the manual-tool target before the corresponding step is shown.
     * If the manual tool isn't currently composed, the host's missing-target grace skip
     * handles the omission.
     */
    fun definition(prepareManualTool: (suspend () -> Unit)? = null): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "setup",
                targetId = SETUP_TARGET,
                title = R.string.tour_dashboard_setup_title.toCaString(),
                body = R.string.tour_dashboard_setup_body.toCaString(),
            ),
            TourStep(
                stepId = "tools",
                targetId = TOOLS_TARGET,
                title = R.string.tour_dashboard_tools_title.toCaString(),
                body = R.string.tour_dashboard_tools_body.toCaString(),
            ),
            TourStep(
                stepId = "mainAction",
                targetId = MAIN_ACTION_TARGET,
                title = R.string.tour_dashboard_main_action_title.toCaString(),
                body = R.string.tour_dashboard_main_action_body.toCaString(),
            ),
            TourStep(
                stepId = "manualTools",
                targetId = MANUAL_TOOL_TARGET,
                title = R.string.tour_dashboard_manual_tools_title.toCaString(),
                body = R.string.tour_dashboard_manual_tools_body.toCaString(),
                prepareTarget = prepareManualTool,
            ),
            TourStep(
                stepId = "settings",
                targetId = SETTINGS_TARGET,
                title = R.string.tour_dashboard_settings_title.toCaString(),
                body = R.string.tour_dashboard_settings_body.toCaString(),
            ),
        ),
    )
}
