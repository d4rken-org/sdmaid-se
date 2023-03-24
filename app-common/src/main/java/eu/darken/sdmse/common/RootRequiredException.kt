package eu.darken.sdmse.common

import androidx.annotation.StringRes
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class RootRequiredException(
    message: String,
    cause: Throwable? = null,
    @StringRes val errorMsgRes: Int = R.string.general_error_root_unavailable
) : IllegalStateException(message, cause), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "RootRequiredException".toCaString(),
        description = errorMsgRes.toCaString()
    )
}