package eu.darken.sdmse.automation.core.errors

open class InvalidSystemStateException(
    message: String,
) : PlanAbortException(message)
