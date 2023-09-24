package eu.darken.sdmse.common

open class ModeUnavailableException(
    message: String? = null,
    cause: Throwable? = null
) : IllegalStateException(message, cause)