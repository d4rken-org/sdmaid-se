package eu.darken.sdmse.common.shizuku

import java.io.IOException

open class ShizukuException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)