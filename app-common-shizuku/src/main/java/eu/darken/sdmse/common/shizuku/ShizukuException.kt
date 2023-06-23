package eu.darken.sdmse.common.shizuku

import java.io.IOException

open class ShizukuException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)