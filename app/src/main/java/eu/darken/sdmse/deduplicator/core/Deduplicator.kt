package eu.darken.sdmse.deduplicator.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.deduplicator.core.deleter.DuplicatesDeleter
import eu.darken.sdmse.deduplicator.core.scanner.DuplicatesScanner
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Deduplicator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val scanner: Provider<DuplicatesScanner>,
    private val deleter: Provider<DuplicatesDeleter>,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.DEDUPLICATOR

    private val usedResources = setOf(gatewaySwitch)
    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    private val lastResult = MutableStateFlow<DeduplicatorTask.Result?>(null)

    override val state: Flow<State> = combine(
        internalData,
        lastResult,
        progress,
    ) { data, lastResult, progress ->
        State(
            data = data,
            lastResult = lastResult,
            progress = progress,
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as DeduplicatorTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is DeduplicatorScanTask -> performScan(task)
                    is DeduplicatorDeleteTask -> performDelete(task)
                    is DeduplicatorOneClickTask -> {
                        performScan()
                        performDelete().let {
                            DeduplicatorOneClickTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths,
                            )
                        }
                    }
                }
            }
            lastResult.value = result
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } catch (e: CancellationException) {
            throw e
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(
        task: DeduplicatorScanTask = DeduplicatorScanTask()
    ): DeduplicatorScanTask.Result {
        log(TAG) { "performScan(): $task" }

        internalData.value = null

        val results = scanner.get().withProgress(this) {
            scan()
        }

        log(TAG, INFO) { "performScan(): ${results.size} clusters found" }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.totalSize }
        log(TAG) { "Field warm up done." }

        internalData.value = Data(
            clusters = results
        )

        return DeduplicatorScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.redundantSize },
        )
    }

    private suspend fun performDelete(
        task: DeduplicatorDeleteTask = DeduplicatorDeleteTask()
    ): DeduplicatorDeleteTask.Success {
        log(TAG) { "performDelete(): $task" }

        val snapshot = internalData.value!!

        val result = deleter.get().withProgress(this) { delete(task, snapshot) }

        updateProgress { Progress.Data() }

        internalData.value = snapshot.prune(result.success.map { it.identifier }.toSet())

        return DeduplicatorDeleteTask.Success(
            affectedSpace = result.success.sumOf { it.size },
            affectedPaths = result.success.map { it.path }.toSet()
        )
    }

    suspend fun exclude(
        identifier: Duplicate.Cluster.Id,
        files: Collection<APath>
    ): Unit = toolLock.withLock {
        log(TAG) { "exclude(): $identifier - $files" }

        val snapshot = internalData.value!!
        val cluster = snapshot.clusters.single { it.identifier == identifier }

        val paths = cluster.groups
            .flatMap { it.duplicates }
            .map { it.path }
            .filter { files.isEmpty() || files.contains(it) }
            .toSet()

        val exclusions = paths
            .map {
                PathExclusion(
                    path = it,
                    tags = setOf(Exclusion.Tag.DEDUPLICATOR),
                )
            }
            .toSet()
        exclusionManager.save(exclusions)

        val newCluster: Duplicate.Cluster? = cluster
            .copy(
                groups = cluster.groups.mapNotNull { group ->
                    val newGroup = when (group.type) {
                        Duplicate.Type.CHECKSUM -> (group as ChecksumDuplicate.Group).copy(
                            duplicates = group.duplicates.filter { !paths.contains(it.path) }.toSet()
                        )

                        Duplicate.Type.PHASH -> (group as PHashDuplicate.Group).copy(
                            duplicates = group.duplicates.filter { !paths.contains(it.path) }.toSet()
                        )
                    }
                    if (newGroup.duplicates.size >= 2) {
                        newGroup
                    } else {
                        log(TAG) { "Group is less than 2 entries: $newGroup" }
                        null
                    }
                }.toSet()
            )
            .takeIf { it.groups.isNotEmpty() }

        if (newCluster == null) log(TAG) { "Cluster was empty after exclusion" }

        internalData.value = snapshot.copy(
            clusters = snapshot.clusters
                .mapNotNull { oldCluster ->
                    if (oldCluster.identifier == identifier) newCluster else oldCluster
                }
                .toSet()
        )
    }

    suspend fun exclude(
        identifiers: Collection<Duplicate.Cluster.Id>
    ): Unit = toolLock.withLock {
        log(TAG) { "exclude(): $identifiers" }

        val snapshot = internalData.value!!

        val exclusions = identifiers
            .map { id -> snapshot.clusters.single { it.identifier == id } }
            .flatMap { it.groups }
            .flatMap { it.duplicates }
            .map { dupe ->
                PathExclusion(
                    path = dupe.path,
                    tags = setOf(Exclusion.Tag.DEDUPLICATOR),
                )
            }
            .toSet()
        exclusionManager.save(exclusions)

        internalData.value = snapshot.copy(
            clusters = snapshot.clusters.filter { !identifiers.contains(it.identifier) }.toSet()
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val lastResult: DeduplicatorTask.Result? = null,
    ) : SDMTool.State

    data class Data(
        val clusters: Set<Duplicate.Cluster> = emptySet(),
    ) {
        val totalSize: Long get() = clusters.sumOf { it.totalSize }
        val redudantSize: Long get() = clusters.sumOf { it.redundantSize }
        val totalCount: Int get() = clusters.size
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Deduplicator): SDMTool
    }

    companion object {
        internal val TAG = logTag("Deduplicator")
    }
}

internal fun Deduplicator.Data.prune(deletedIds: Set<Duplicate.Id>): Deduplicator.Data {
    val newClusters = this.clusters
        .asSequence()
        .map { oldCluster ->
            // Remove duplicates from groups
            val newGroups = oldCluster.groups
                .map { oldGroup ->
                    val newDuplicates: Set<Duplicate> = oldGroup.duplicates
                        .filter { toDelete ->
                            val wasDeleted = deletedIds.contains(toDelete.identifier)
                            if (wasDeleted) log(Deduplicator.TAG) { "Prune: Deleted duplicate: $toDelete" }
                            !wasDeleted
                        }
                        .toSet()
                    when (oldGroup.type) {
                        Duplicate.Type.CHECKSUM -> {
                            oldGroup as ChecksumDuplicate.Group
                            @Suppress("UNCHECKED_CAST")
                            oldGroup.copy(duplicates = newDuplicates as Set<ChecksumDuplicate>)
                        }

                        Duplicate.Type.PHASH -> {
                            oldGroup as PHashDuplicate.Group
                            @Suppress("UNCHECKED_CAST")
                            oldGroup.copy(duplicates = newDuplicates as Set<PHashDuplicate>)
                        }
                    }
                }
                .filter {
                    // group may be empty after removing duplicates
                    val empty = it.duplicates.isEmpty()
                    if (empty) log(Deduplicator.TAG) { "Prune: Empty group: $it" }
                    !empty
                }
                .toSet()
            oldCluster.copy(groups = newGroups)
        }
        .filter {
            val isSolo = it.count < 2
            if (isSolo) log(Deduplicator.TAG) { "Prune: Cluster only has one item: $it" }
            !isSolo
        }
        .toSet()

    return this.copy(clusters = newClusters)
}