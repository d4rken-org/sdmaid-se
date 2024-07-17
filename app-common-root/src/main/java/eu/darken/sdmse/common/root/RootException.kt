package eu.darken.sdmse.common.root

import java.io.IOException

open class RootException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)