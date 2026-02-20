package eu.darken.sdmse.automation.core.errors

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class AutomationSchedulerException(
    override val cause: Throwable,
) : AutomationException(cause = cause), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.automation_error_scheduler_title.toCaString(),
        description = caString {
            val sb = StringBuilder(it.getString(R.string.automation_error_scheduler_body))
            if (cause is HasLocalizedError) {
                sb.append("\n\n")
                sb.append((cause as HasLocalizedError).getLocalizedError().description.get(it))
            }
            sb.toString()
        },
    )
}
