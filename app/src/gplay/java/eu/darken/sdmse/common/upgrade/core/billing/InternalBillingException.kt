package eu.darken.sdmse.common.upgrade.core.billing

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class InternalBillingException(cause: Throwable) :
    BillingException("An internal Google Play error occurred.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_internal_error_title.toCaString(),
        description = R.string.upgrades_gplay_internal_error_description.toCaString(),
    )
}
