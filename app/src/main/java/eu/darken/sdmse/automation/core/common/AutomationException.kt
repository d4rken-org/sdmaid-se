package eu.darken.sdmse.automation.core.common

open class AutomationException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}