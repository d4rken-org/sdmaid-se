package eu.darken.sdmse.deduplicator.core.deleter

import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class DuplicatesDeleter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.DEFAULT_STATE)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    fun delete(
        task: DeduplicatorDeleteTask,
        data: Deduplicator.Data
    ): Deleted {
        log(TAG) { "Processing $task" }

        return when (task.mode) {
            is DeduplicatorDeleteTask.TargetMode.All -> targetAll(task.mode, data)
            is DeduplicatorDeleteTask.TargetMode.Clusters -> targetClusters(task.mode, data)
            is DeduplicatorDeleteTask.TargetMode.Groups -> targetGroups(task.mode, data)
            is DeduplicatorDeleteTask.TargetMode.Duplicates -> targetDuplicates(task.mode, data)
        }
    }

    private fun targetAll(
        mode: DeduplicatorDeleteTask.TargetMode.All,
        data: Deduplicator.Data
    ): Deleted {
        log(TAG) { "targetAll(): $mode" }
        TODO()
    }

    private fun targetClusters(
        mode: DeduplicatorDeleteTask.TargetMode.Clusters,
        data: Deduplicator.Data
    ): Deleted {
        log(TAG) { "targetClusters(): $mode" }
        TODO()
    }

    private fun targetGroups(
        mode: DeduplicatorDeleteTask.TargetMode.Groups,
        data: Deduplicator.Data
    ): Deleted {
        log(TAG) { "targetGroups(): $mode" }
        TODO()
    }

    private fun targetDuplicates(
        mode: DeduplicatorDeleteTask.TargetMode.Duplicates,
        data: Deduplicator.Data
    ): Deleted {
        log(TAG) { "targetDuplicates(): $mode" }
        TODO()
    }

    data class Deleted(
        val clusters: Set<Duplicate.Cluster.Identifier> = emptySet(),
        val groups: Set<Duplicate.Group.Identifier> = emptySet(),
        val duplicates: Set<String> = emptySet(),
        val removed: Int = 0,
        val freed: Long = 0L,
    )

    companion object {
        private val TAG = logTag("Deduplicator", "Deleter")
    }
}