package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.exclusion.core.ExclusionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import javax.inject.Inject


class DuplicatesScanner @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.DEFAULT_STATE)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun scan(sleuths: Collection<Sleuth>): Collection<Duplicate.Cluster> {
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
                        identifier = Duplicate.Cluster.Identifier(UUID.randomUUID().toString()),
                        groups = setOf(it)
                    )
                }
            }
            .flatten()

        log(TAG) { "clusters=${clusters.size}" }
        return clusters
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Scanner")
    }
}