package eu.darken.sdmse.automation.core.errors

open class AutomationException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}