package eu.darken.sdmse.common.root

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.root.javaroot.internal.RootException

class RootUnavailableException(
    message: String,
    cause: Throwable? = null,
    @StringRes val errorMsgRes: Int = R.string.general_error_root_unavailable
) : RootException(message, cause), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = "RootUnavailableException",
        description = context.getString(errorMsgRes)
    )
}