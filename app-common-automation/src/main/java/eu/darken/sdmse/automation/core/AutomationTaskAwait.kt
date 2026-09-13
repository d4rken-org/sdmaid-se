package eu.darken.sdmse.automation.core

import eu.darken.sdmse.automation.core.errors.AutomationNotRunningException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Awaits an automation task and decides what a cancellation of it means to us.
 *
 * Three cases, and only the middle one changes what callers see:
 * - our own coroutine is gone: the caller was cancelled, so the cancellation travels unchanged,
 *   after the task has been cancelled and joined.
 * - we are still running and the task carries [UserCancelledAutomationException]: somebody chose
 *   to stop (overlay Cancel, `cancelTask()`, the TV leave-guard) and that stays a cancellation.
 * - we are still running and it does not: the service's own scope died underneath us, so the
 *   accessibility service is simply gone. That is a failure. Reporting it as a cancellation makes
 *   every caller above treat a finished run as an abandoned one and discard what it completed.
 *
 * [tag] and [logId] are the caller's, so the log lines keep reading as they did before the
 * extraction.
 */
internal suspend fun <T> Deferred<T>.awaitAutomationResult(tag: String, logId: String): T = try {
    await()
} catch (e: CancellationException) {
    if (currentCoroutineContext().isActive) {
        if (e !is UserCancelledAutomationException) {
            log(tag, WARN) { "$logId: Task died with the accessibility service: ${e.asLog()}" }
            throw AutomationNotRunningException()
        }
        // The task itself was cancelled (overlay Cancel, cancelTask()), await() only returned
        // after its teardown finished.
        log(tag, INFO) { "$logId: Task was cancelled: $e" }
    } else {
        // The task is not a child of this caller. Without cancelling and joining it here,
        // taskLock would be released while the task is still running, so a second automation
        // could overlap with it.
        log(tag, INFO) { "$logId: Caller was cancelled, cancelling task and awaiting teardown" }
        cancel(CancellationException("$logId: Caller was cancelled").apply { initCause(e) })
        withContext(NonCancellable) { join() }
        log(tag) { "$logId: Task teardown finished" }
    }
    throw e
}
