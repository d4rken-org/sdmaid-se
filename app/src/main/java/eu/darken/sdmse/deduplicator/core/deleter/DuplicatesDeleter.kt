package eu.darken.sdmse.deduplicator.core.deleter

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.delete
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class DuplicatesDeleter @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val arbiter: DuplicatesArbiter,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.DEFAULT_STATE)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun delete(
        task: DeduplicatorDeleteTask,
        data: Deduplicator.Data
    ): Deleted {
        log(TAG) { "Processing $task" }

        val deletedDupes = when (task.mode) {
            is DeduplicatorDeleteTask.TargetMode.All -> data.targetAll()
            is DeduplicatorDeleteTask.TargetMode.Clusters -> data.targetClusters(
                targets = task.mode.targets,
                deleteAll = task.mode.deleteAll,
            )

            is DeduplicatorDeleteTask.TargetMode.Groups -> data.targetGroups(
                targets = task.mode.targets,
                deleteAll = task.mode.deleteAll,
            )

            is DeduplicatorDeleteTask.TargetMode.Duplicates -> data.targetDuplicates(
                targets = task.mode.targets,
            )
        }

        log(TAG) { "Deletion finished, deleted ${deletedDupes.size} duplicates" }

        return Deleted(
            success = deletedDupes.map { it.identifier }.toSet(),
        )
    }

    private suspend fun Deduplicator.Data.targetAll(): Collection<Duplicate> {
        log(TAG, VERBOSE) { "targetAll()" }
        return targetClusters(clusters.map { it.identifier }.toSet())
    }

    private suspend fun Deduplicator.Data.targetClusters(
        targets: Set<Duplicate.Cluster.Id>,
        deleteAll: Boolean = false,
    ): Collection<Duplicate> {
        log(TAG, VERBOSE) { "#targetClusters(deleteAll=$deleteAll): $targets" }
        return clusters
            .filter { cluster -> targets.contains(cluster.identifier) }
            .map { cluster ->
                log(TAG, VERBOSE) { "_targetClusters(): Deleting from ${cluster.identifier}" }
                if (deleteAll) {
                    targetGroups(
                        targets = cluster.groups.map { it.identifier }.toSet(),
                        deleteAll = true,
                    )
                } else {
                    val (favorite, rest) = arbiter.decideGroups(cluster.groups)
                    log(TAG, VERBOSE) { "_targetClusters(): Our favorite is $favorite" }

                    val favoriteResult = targetGroups(
                        targets = setOf(favorite.identifier),
                        deleteAll = false, // !!
                    )
                    val restResult = targetGroups(
                        targets = rest.map { it.identifier }.toSet(),
                        deleteAll = true,
                    )
                    favoriteResult + restResult
                }
            }
            .flatten()
    }

    private suspend fun Deduplicator.Data.targetGroups(
        targets: Set<Duplicate.Group.Id>,
        deleteAll: Boolean = false,
    ): Collection<Duplicate> {
        log(TAG, VERBOSE) { "#_targetGroups(deleteAll=$deleteAll): $targets" }
        return clusters
            .flatMap { it.groups }
            .filter { group -> targets.contains(group.identifier) }
            .map { group ->
                log(TAG, VERBOSE) { "__targetGroups(): Deleting from ${group.identifier}" }
                if (deleteAll) {
                    targetDuplicates(group.duplicates.map { it.identifier }.toSet())
                } else {
                    val (favorite, rest) = arbiter.decideDuplicates(group.duplicates)
                    log(TAG, VERBOSE) { "__targetGroups(): Our favorite is $favorite" }

                    targetDuplicates(rest.map { it.identifier }.toSet())
                }
            }
            .flatten()
    }

    private suspend fun Deduplicator.Data.targetDuplicates(
        targets: Set<Duplicate.Id>
    ): Collection<Duplicate> {
        log(TAG, VERBOSE) { "#__targetDuplicates(): $targets" }
        return clusters
            .flatMap { it.groups }
            .flatMap { it.duplicates }
            .filter { targets.contains(it.identifier) }
            .onEach { dupe ->
                // targetDuplicates() get's direct targets, no need to make a specific selection
                log(TAG, VERBOSE) { "___targetDuplicates(): Deleting ${dupe.identifier}" }
                dupe.path.delete(gatewaySwitch)
            }
    }

    data class Deleted(
        val success: Set<Duplicate.Id> = emptySet(),
    )

    companion object {
        private val TAG = logTag("Deduplicator", "Deleter")
    }
}