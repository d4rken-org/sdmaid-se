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
    ): Result {
        log(TAG) { "Processing $task" }

        when (task.mode) {
            DeduplicatorDeleteTask.TargetMode.All -> {}
            is DeduplicatorDeleteTask.TargetMode.Clusters -> {}
            is DeduplicatorDeleteTask.TargetMode.Duplicates -> {}
            is DeduplicatorDeleteTask.TargetMode.Groups -> {}
        }

        return TODO()
    }

    data class Result(
        val deletedClusters: Set<Duplicate.Cluster.Identifier>,
        val deletedGroups: Set<Duplicate.Group.Identifier>,
        val deletedDuplicates: Set<Duplicate>,
        val removedFiles: Int,
        val freedSpace: Long,
    )

    companion object {
        private val TAG = logTag("Deduplicator", "Deleter")
    }
}