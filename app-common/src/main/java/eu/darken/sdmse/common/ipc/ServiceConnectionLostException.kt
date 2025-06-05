package eu.darken.sdmse.common.ipc

import android.os.DeadObjectException
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.error.getStackTracePeek

class ServiceConnectionLostException(
    override val cause: DeadObjectException
) : Exception(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.general_error_ipc_deadobject_title.toCaString(),
        description = caString {
            var message = it.getString(R.string.general_error_ipc_deadobject_description)
            message += "\n\n"
            message += cause.getStackTracePeek()
            message
        }
    )

}