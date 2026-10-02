package eu.darken.sdmse.appcontrol.core.restore

import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

/**
 * The system settings show the Restore button for this app, but disabled.
 */
class RestoreUnavailableException(
    message: String,
) : PlanAbortException(message), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.appcontrol_restore_unavailable_title.toCaString(),
        description = R.string.appcontrol_restore_unavailable_body.toCaString(),
    )
}
