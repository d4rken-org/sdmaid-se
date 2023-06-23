package eu.darken.sdmse.common.shizuku

class ShizukuUnavailableException(
    message: String,
    cause: Throwable? = null,
) : ShizukuException(message, cause)