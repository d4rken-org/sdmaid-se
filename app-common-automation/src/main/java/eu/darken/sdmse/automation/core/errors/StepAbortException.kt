package eu.darken.sdmse.automation.core.errors

/**
 * Aborts the current step without retrying it.
 *
 * [treatAsSuccess] distinguishes a deliberate skip (the step is unnecessary because another path
 * handles it) from a genuine failure (the step could not be completed). Only a skip may let the
 * plan continue - a failure must propagate, otherwise the caller reports success for work that
 * never happened.
 */
class StepAbortException(
    message: String,
    val treatAsSuccess: Boolean = false,
) : AutomationException(message)
