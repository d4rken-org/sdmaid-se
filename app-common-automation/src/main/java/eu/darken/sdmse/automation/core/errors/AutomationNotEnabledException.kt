package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

open class AutomationNotEnabledException(
    override val message: String = "Accessibility service is not enabled"
) : AutomationUnavailableException(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_not_enabled_title.toCaString(),
        description = R.string.automation_error_not_enabled_body.toCaString(),
    )

}