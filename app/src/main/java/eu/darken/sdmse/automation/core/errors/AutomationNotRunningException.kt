package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

open class AutomationNotRunningException(
    override val message: String = "Accessibility service isn't running"
) : AutomationUnavailableException(), HasLocalizedError {
    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_not_running_title.toCaString(),
        description = R.string.automation_error_not_running_body.toCaString(),
    )
}