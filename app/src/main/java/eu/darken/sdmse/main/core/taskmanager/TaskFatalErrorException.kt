package eu.darken.sdmse.main.core.taskmanager

/**
 * Wraps a non-[Exception] [Throwable] (e.g. an [OutOfMemoryError]) that escaped a tool, so callers
 * of `TaskManager.submit()` keep receiving an [Exception] while the original cause is preserved.
 */
class TaskFatalErrorException(
    override val cause: Throwable,
) : RuntimeException("Task failed with a fatal error: ${cause::class.simpleName}", cause)
