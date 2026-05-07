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
     * Builds the tour definition. The screen passes [includeSetup] / [includeManualTool] booleans
     * derived from the dashboard's current items so steps whose target card is absent are dropped
     * up-front. Without this filter the host would render an invisible overlay for the missing
     * target's grace window (600 ms) and then auto-skip — visible to the user as the tour starting
     * on a later step, or as Previous "closing" the tour briefly when it lands on the absent step.
     *
     * [prepareManualTool] is the dashboard's scroll-to-card hook for the manual-tool step.
     */
    fun definition(
        includeSetup: Boolean = true,
        includeManualTool: Boolean = true,
        prepareManualTool: (suspend () -> Unit)? = null,
    ): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = buildList {
            if (includeSetup) {
                add(
                    TourStep(
                        stepId = "setup",
                        targetId = SETUP_TARGET,
                        title = R.string.tour_dashboard_setup_title.toCaString(),
                        body = R.string.tour_dashboard_setup_body.toCaString(),
                    ),
                )
            }
            add(
                TourStep(
                    stepId = "tools",
                    targetId = TOOLS_TARGET,
                    title = R.string.tour_dashboard_tools_title.toCaString(),
                    body = R.string.tour_dashboard_tools_body.toCaString(),
                ),
            )
            add(
                TourStep(
                    stepId = "mainAction",
                    targetId = MAIN_ACTION_TARGET,
                    title = R.string.tour_dashboard_main_action_title.toCaString(),
                    body = R.string.tour_dashboard_main_action_body.toCaString(),
                ),
            )
            if (includeManualTool) {
                add(
                    TourStep(
                        stepId = "manualTools",
                        targetId = MANUAL_TOOL_TARGET,
                        title = R.string.tour_dashboard_manual_tools_title.toCaString(),
                        body = R.string.tour_dashboard_manual_tools_body.toCaString(),
                        prepareTarget = prepareManualTool,
                    ),
                )
            }
            add(
                TourStep(
                    stepId = "settings",
                    targetId = SETTINGS_TARGET,
                    title = R.string.tour_dashboard_settings_title.toCaString(),
                    body = R.string.tour_dashboard_settings_body.toCaString(),
                ),
            )
        },
    )
}
