package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** The target's cache size as StorageStats reports it. */
internal sealed interface CacheStats {
    /** No answer: the query failed, returned nothing, or did not return in time. */
    data object Unavailable : CacheStats

    data object Zero : CacheStats

    data class Positive(val bytes: Long) : CacheStats

    companion object {
        fun of(bytes: Long?): CacheStats = when {
            bytes == null || bytes < 0 -> Unavailable
            bytes == 0L -> Zero
            else -> Positive(bytes)
        }
    }
}

internal fun CacheStats.isBelow(baseline: CacheStats.Positive): Boolean = when (this) {
    CacheStats.Unavailable -> false
    CacheStats.Zero -> true
    is CacheStats.Positive -> bytes < baseline.bytes
}

/**
 * Reads [query] on a job in [scope] and awaits that job with a timeout.
 *
 * The query is a blocking Binder call, so a timeout around it would not return before the call does.
 * A read that times out is left running, and while it runs, every further read joins it instead of
 * starting a second one.
 */
internal class CacheStatsReader(
    private val scope: CoroutineScope,
    private val tag: String,
    private val query: suspend () -> Long?,
) {
    private var inFlight: Deferred<Long?>? = null

    suspend fun read(timeoutMs: Long): CacheStats {
        val running = inFlight?.takeIf { !it.isCompleted }
        if (running != null) log(tag, INFO) { "CacheStats: earlier read still running, joining it" }
        val job = running ?: scope.async {
            try {
                query()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "CacheStats: query failed: ${e.asLog()}" }
                null
            }
        }.also { inFlight = it }

        val bytes = try {
            withTimeoutOrNull(timeoutMs) { job.await() }
        } catch (e: CancellationException) {
            // The job was cancelled with its scope, not us.
            currentCoroutineContext().ensureActive()
            null
        }
        if (!job.isCompleted) log(tag, WARN) { "CacheStats: no answer after ${timeoutMs}ms, leaving the read behind" }
        return CacheStats.of(bytes)
    }
}
