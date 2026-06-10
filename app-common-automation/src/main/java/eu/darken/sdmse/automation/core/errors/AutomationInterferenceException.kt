package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.automation.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.pkgs.Pkg

/**
 * Thrown when a foreign app (e.g. an app-locker like Avast App Lock) is holding the system
 * settings window instead of the screen we are trying to automate, preventing us from reaching
 * the target UI.
 *
 * Subclasses [InvalidSystemStateException] so the whole automation run is aborted immediately
 * (instead of grinding through retries until the generic compatibility/timeout error) and tells
 * the user which app to adjust.
 */
class AutomationInterferenceException(
    val blockerPkg: Pkg.Id,
    val blockerLabel: String?,
) : InvalidSystemStateException(
    "System settings window is being blocked by $blockerPkg (${blockerLabel ?: "?"})"
), HasLocalizedError {

    private val displayName: String = blockerLabel ?: blockerPkg.name

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.automation_error_interference_title.toCaString(),
        description = R.string.automation_error_interference_body.toCaString(displayName),
    )
}
