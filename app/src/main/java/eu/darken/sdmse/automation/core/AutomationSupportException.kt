package eu.darken.sdmse.automation.core

class AutomationSupportException(
    message: String,
    cause: Throwable
) : UnsupportedOperationException(message, cause)