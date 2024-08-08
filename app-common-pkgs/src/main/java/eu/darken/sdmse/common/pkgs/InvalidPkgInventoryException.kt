package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class InvalidPkgInventoryException(
    override val message: String
) : IllegalStateException(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.pkgrepo_invalid_inventory_error_title.toCaString(),
        description = R.string.pkgrepo_invalid_inventory_error_description.toCaString(),
    )
}