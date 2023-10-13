package eu.darken.sdmse.automation.core.common

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class ScreenUnavailableException(
    message: String,
) : PlanAbortException(message), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.automation_error_screen_unavailable_title.toCaString(),
        description = R.string.automation_error_screen_unavailable_body.toCaString()
    )
}
