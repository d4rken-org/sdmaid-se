package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

open class AutomationCompatibilityException(
    override val message: String = "SD Maid couldn’t figure out the screen layout. If this keeps happening, your language or setup might not be fully supported. Check for updates or reach out to me so I can fix it."
) : AutomationException(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_no_consent_title.toCaString(),
        description = R.string.automation_error_compatibility_body.toCaString(),
    )

}