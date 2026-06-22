package eu.darken.sdmse.common.upgrade

import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.main.core.SDMTool

/**
 * Thrown at a tool's task-submit boundary when a Pro-only task is submitted by a non-Pro user.
 *
 * This is a defense-in-depth safety net: the UI is expected to gate Pro features before submitting,
 * but if a UI mistake lets a Pro-only task through, the backend refuses it here instead of silently
 * performing the work for free.
 *
 * The localized title + description are provided directly; the upgrade navigation action is attached
 * by the app-level error dialog customizer (which has access to the upgrade route).
 */
class UpgradeRequiredException(
    val tool: SDMTool.Type? = null,
) : IllegalStateException(
    "Pro upgrade required" + (tool?.let { " for $it" } ?: ""),
), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.general_error_upgrade_required_title.toCaString(),
        description = R.string.general_error_upgrade_required_description.toCaString(),
    )
}
