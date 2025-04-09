package eu.darken.sdmse.automation.core.errors

open class PlanAbortException(
    message: String,
    val treatAsSuccess: Boolean = false,
) : AutomationException(message)
