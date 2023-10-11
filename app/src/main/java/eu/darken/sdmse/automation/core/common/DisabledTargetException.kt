package eu.darken.sdmse.automation.core.common

open class DisabledTargetException(message: String?, cause: Throwable?) : AutomationException(message, cause) {
    constructor(message: String?) : this(message, null)
}