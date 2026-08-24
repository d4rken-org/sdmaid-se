package eu.darken.sdmse.main.core

import kotlinx.coroutines.CancellationException

/**
 * A tool failed after completing part of its work. [partialResult] carries what succeeded;
 * [cause] is the failure and is what callers should surface. TaskManager unwraps this at the
 * completion boundary: ManagedTask records both, callers of submit() see only [cause].
 */
class PartialResultException(
    override val cause: Throwable,
    val partialResult: SDMTool.Task.Result,
) : Exception("Partial result before failure: $partialResult", cause) {

    init {
        // Wrapping a cancellation would let TaskManager consume it as an ordinary failure and
        // publish a partial result for a cancelled task.
        require(cause !is CancellationException) { "Cancellations must propagate unwrapped" }
    }
}
