package eu.darken.sdmse.common.root

import androidx.annotation.StringRes
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class RootUnavailableException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
    @StringRes val errorMsgRes: Int = eu.darken.sdmse.common.R.string.general_error_root_unavailable
) : RootException(message = message, cause = cause), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "RootUnavailableException".toCaString(),
        description = errorMsgRes.toCaString()
    )
}