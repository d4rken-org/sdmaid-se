package eu.darken.sdmse.automation.core.errors

open class AutomationException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}