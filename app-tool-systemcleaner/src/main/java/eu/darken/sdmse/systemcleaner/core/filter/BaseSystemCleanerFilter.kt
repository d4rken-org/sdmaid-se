package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.delete
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class BaseSystemCleanerFilter : SystemCleanerFilter {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun Collection<SystemCleanerFilter.Match.Deletion>.deleteAll(
        gatewaySwitch: GatewaySwitch
    ): Collection<SystemCleanerFilter.Processed> {
        val uniqueRoots = this.map { it.lookup }.filterDistinctRoots()
        val uniqueMatches = this.filter { it.lookup in uniqueRoots }

        if (uniqueMatches.size != uniqueRoots.size) {
            throw IllegalStateException("Matches (${uniqueMatches.size}) != Roots (${uniqueRoots.size})")
        }

        updateProgressCount(Progress.Count.Percent(uniqueRoots.size))

        return uniqueMatches.map { match ->
            updateProgressPrimary(match.lookup.userReadablePath)
            var error: Throwable? = null
            try {
                match.lookup.delete(gatewaySwitch, recursive = true)
            } catch (e: Exception) {
                log(identifier, WARN) { "Failed to delete ${match.lookup}" }
                error = e
            } finally {
                increaseProgress()
            }
            SystemCleanerFilter.Processed(
                match = match,
                error = error,
            )
        }
    }
}