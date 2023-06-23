package eu.darken.sdmse.common.root

import java.io.IOException

open class RootException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)