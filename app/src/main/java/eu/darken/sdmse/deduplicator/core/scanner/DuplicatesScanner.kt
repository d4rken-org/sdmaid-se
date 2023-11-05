package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import javax.inject.Inject


class DuplicatesScanner @Inject constructor() : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.DEFAULT_STATE)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun scan(sleuths: Collection<Sleuth>): Set<Duplicate.Cluster> {
        log(TAG) { "scan()" }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressCount(Progress.Count.Indeterminate())

        val allSleuthResults = sleuths.map { sleuth ->
            val sleuthResults = sleuth.withProgress(this) { investigate() }
            sleuth to sleuthResults
        }
        log(TAG) { "allSleuthResults=${allSleuthResults.size}" }

        val clusters = allSleuthResults
            .map { (sleuth, groups) ->
                groups.map {
                    Duplicate.Cluster(
                        identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                        groups = setOf(it)
                    )
                }
            }
            .flatten()
            .toSet()

        log(TAG) { "clusters=${clusters.size}" }
        return clusters
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Scanner")
    }
}