package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.Corpse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class CorpseFilter(
    private val tag: String,
    private val defaultProgress: Progress.Data
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(defaultProgress)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(500)

    /**
     * API level from which on this filter's findings are no longer validated.
     *
     * From it on the filter still scans, but its corpses are logged and then dropped instead of
     * returned, so a debug log from an affected device shows what the filter would have found
     * without any of it reaching the UI or the uninstall watcher's automatic deletion.
     *
     * Different from a filter that bails before scanning: that marks a location which no longer
     * exists, where scanning could only ever come up empty.
     */
    internal open val untestedApiCeiling: Int? = null

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun scan(): Collection<Corpse> = try {
        val ceiling = untestedApiCeiling
        if (ceiling != null && hasApiLevel(ceiling)) scanForLogOnly(ceiling) else doScan()
    } finally {
        progressPub.value = defaultProgress
        log(tag) { "Scan finished" }
    }

    /**
     * Runs the scan for the debug log alone: whatever it finds is recorded and dropped, and a
     * failure is recorded and swallowed.
     *
     * Swallowing matters as much as withholding. The scan reaches code this Android version has
     * never been validated against, and letting it fail the task would turn a scan that used to
     * end quietly on such a device into a visibly broken one.
     */
    private suspend fun scanForLogOnly(ceiling: Int): Collection<Corpse> {
        val found = try {
            doScan()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Cancellation doesn't always arrive as a CancellationException: LocalGateway wraps
            // everything its operations throw, cancellation included, into a ReadException.
            currentCoroutineContext().ensureActive()
            log(tag, ERROR) { "Untested API level ($ceiling): scan failed: ${e.asLog()}" }
            return emptySet()
        }

        log(tag, WARN) { "Untested API level ($ceiling): withholding ${found.size} corpses." }
        found.forEach { log(tag, WARN) { "Withheld corpse: $it" } }
        return emptySet()
    }

    override fun toString(): String = "${this::class.simpleName}(${hashCode()})"

    internal abstract suspend fun doScan(): Collection<Corpse>

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): CorpseFilter
    }
}