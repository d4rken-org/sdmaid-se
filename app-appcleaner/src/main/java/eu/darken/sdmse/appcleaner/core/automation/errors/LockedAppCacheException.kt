package eu.darken.sdmse.appcleaner.core.automation.errors

import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class LockedAppCacheException(
    message: String,
) : PlanAbortException(message), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.appcleaner_automation_error_locked_appcache_title.toCaString(),
        description = R.string.appcleaner_automation_error_locked_appcache_body.toCaString()
    )
}
