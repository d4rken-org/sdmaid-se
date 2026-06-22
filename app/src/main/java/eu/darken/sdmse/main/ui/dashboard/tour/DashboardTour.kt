package eu.darken.sdmse.main.ui.dashboard.tour

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.GuidedTour
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep

object DashboardTour : GuidedTour {

    override val id: TourId = TourId("tour.dashboard")

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
     * The leading "overview" step is centerless (no anchor) — it introduces the dashboard concept
     * itself before subsequent steps focus on individual cards/controls.
     *
     * [prepareSetup] / [prepareTools] / [prepareManualTool] are scroll-to-card hooks. They matter
     * because the dashboard's `LazyVerticalGrid` only composes visible items — without scrolling,
     * an off-screen target never registers and the step would grace-skip. Setup also needs a hook
     * for the Previous-navigation case: once Tools has scrolled the grid past the setup card,
     * stepping back to Setup would otherwise leave that card off-screen above the viewport.
     */
    fun definition(
        includeSetup: Boolean = true,
        includeManualTool: Boolean = true,
        prepareSetup: (suspend () -> Unit)? = null,
        prepareTools: (suspend () -> Unit)? = null,
        prepareManualTool: (suspend () -> Unit)? = null,
    ): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = buildList {
            add(
                TourStep(
                    stepId = "overview",
                    targetId = null,
                    body = R.string.tour_dashboard_overview_body.toCaString(),
                ),
            )
            if (includeSetup) {
                add(
                    TourStep(
                        stepId = "setup",
                        targetId = SETUP_TARGET,
                        body = R.string.tour_dashboard_setup_body.toCaString(),
                        prepareTarget = prepareSetup,
                    ),
                )
            }
            add(
                TourStep(
                    stepId = "tools",
                    targetId = TOOLS_TARGET,
                    body = R.string.tour_dashboard_tools_body.toCaString(),
                    prepareTarget = prepareTools,
                ),
            )
            add(
                TourStep(
                    stepId = "mainAction",
                    targetId = MAIN_ACTION_TARGET,
                    body = R.string.tour_dashboard_main_action_body.toCaString(),
                ),
            )
            if (includeManualTool) {
                add(
                    TourStep(
                        stepId = "manualTools",
                        targetId = MANUAL_TOOL_TARGET,
                        body = R.string.tour_dashboard_manual_tools_body.toCaString(),
                        prepareTarget = prepareManualTool,
                    ),
                )
            }
            add(
                TourStep(
                    stepId = "settings",
                    targetId = SETTINGS_TARGET,
                    body = R.string.tour_dashboard_settings_body.toCaString(),
                ),
            )
        },
    )
}
