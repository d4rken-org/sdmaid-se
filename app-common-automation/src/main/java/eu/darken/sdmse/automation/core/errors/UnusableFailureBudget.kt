package eu.darken.sdmse.automation.core.errors

/**
 * Failures that mean the automation path could not be driven at all, as opposed to a single
 * target being uncooperative. A timeout is the slow form, an unretryable step abort the fast one.
 * Both indicate that the automation path itself is broken, so both feed the give-up heuristic.
 */
fun Throwable.isAutomationUnusable(): Boolean = when (this) {
    is AutomationTimeoutException -> true
    is StepAbortException -> !treatAsSuccess
    else -> false
}

/** How many targets may fail with an unusable automation path before we stop trying. */
const val AUTOMATION_FAILURE_LIMIT = 8

/**
 * Counts the targets that failed with an unusable automation path. The count is a total across
 * the whole batch, it never resets: successes and skipped targets report nothing.
 */
class UnusableFailureBudget(private val limit: Int = AUTOMATION_FAILURE_LIMIT) {

    private var counter = 0

    fun onFailure(error: Throwable) {
        if (error.isAutomationUnusable()) counter++
    }

    val isExhausted: Boolean
        get() = counter >= limit
}
