package eu.darken.sdmse.common.files

import androidx.annotation.Keep
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.error.localized
import java.io.IOException

@Keep
open class PathException(
    message: String? = "Error during access.",
    val path: APath?,
    cause: Throwable? = null,
) : IOException(if (path != null) "$message <-> ${path.path}" else message, cause)

@Keep
open class ReadException @JvmOverloads constructor(
    message: String? = "Can't read from path.",
    path: APath? = null,
    cause: Throwable? = null,
) : PathException(message = message, cause = cause, path = path), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "ReadException".toCaString(),
        description = caString { cx ->
            val sb = StringBuilder()
            sb.append(
                path?.let {
                    cx.getString(
                        eu.darken.sdmse.common.R.string.general_error_cant_access_msg,
                        it.userReadablePath.get(cx)
                    )
                } ?: message
            )
            cause?.let {
                sb.append("\n\n")
                val localizedCause = it.localized(cx)
                sb.append(localizedCause.asText().get(cx))
            }
            sb.toString()
        }
    )
}

@Keep
class WriteException @JvmOverloads constructor(
    message: String? = "Can't write to path.",
    path: APath? = null,
    cause: Throwable? = null,
) : PathException(message = message, cause = cause, path = path), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "WriteException".toCaString(),
        description = caString { cx ->
            val sb = StringBuilder()
            sb.append(
                path?.let {
                    cx.getString(
                        eu.darken.sdmse.common.R.string.general_error_cant_access_msg,
                        it.userReadablePath.get(cx)
                    )
                } ?: message
            )
            cause?.let {
                sb.append("\n\n")
                val localizedCause = it.localized(cx)
                sb.append(localizedCause.asText().get(cx))
            }
            sb.toString()
        }
    )
}