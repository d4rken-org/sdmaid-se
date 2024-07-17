package eu.darken.sdmse.common.shizuku

class ShizukuUnavailableException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
) : ShizukuException(message = message, cause = cause)