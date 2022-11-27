package eu.darken.sdmse.common.files.ui.picker

import android.content.Context
import eu.darken.sdmse.R
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class EmptyResultException
    : IllegalStateException("The result was empty."), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = "EmptyResultException",
        description = context.getString(R.string.general_error_empty_result_msg)
    )
}