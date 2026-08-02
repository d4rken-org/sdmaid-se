package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.automation.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

/**
 * The app's settings page exists but can't be opened while the app is disabled.
 * Unlike [NoSettingsWindowException] this is recoverable: enabling the app, or using
 * root/ADB instead of the accessibility service, makes the cache clearable again.
 */
class DisabledAppException(
    message: String,
) : PlanAbortException(message), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.automation_error_disabled_app_title.toCaString(),
        description = R.string.automation_error_disabled_app_body.toCaString()
    )
}
