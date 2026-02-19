package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.error.localized

class SystemCleanerFilterException(
    val filter: SystemCleanerFilter,
    override val cause: Throwable
) : Exception(), HasLocalizedError {

    override val message: String
        get() = "SystemCleaner filter failed: $filter"

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = eu.darken.sdmse.common.R.string.general_error_label.toCaString(),
        description = caString {
            val subReason = cause.localized(it).asText().get(it)
            "SystemCleaner filter failed: $filter.\n\n$subReason"
        }
    )
}