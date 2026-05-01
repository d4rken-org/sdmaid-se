package eu.darken.sdmse.deduplicator.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.deleter.DuplicatesDeleter
import eu.darken.sdmse.deduplicator.core.scanner.DuplicatesScanner
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Deduplicator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val exclusionManager: ExclusionManager,
    private val scanner: Provider<DuplicatesScanner>,
    private val deleter: Provider<DuplicatesDeleter>,
    private val settings: DeduplicatorSettings,
    private val arbiter: DuplicatesArbiter,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.DEDUPLICATOR

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

    init {
        settings.arbiterConfig.flow
            .drop(1)
            .onEach {
                toolLock.withLock {
                    log(TAG, INFO) { "Arbiter config changed, clearing scan data" }
                    internalData.value = null
                }
            }
            .launchIn(appScope)
    }
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as DeduplicatorTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(gatewaySwitch, fileForensics) {
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

        val scanOptions = DuplicatesScanner.Options(
            paths = task.paths ?: settings.scanPaths.value().paths,
            minimumSize = settings.minSizeBytes.value(),
            skipUncommon = settings.skipUncommon.value(),
            useSleuthChecksum = settings.isSleuthChecksumEnabled.value(),
            useSleuthPHash = settings.isSleuthPHashEnabled.value(),
            useSleuthMedia = settings.isSleuthMediaEnabled.value(),
        )

        val results = scanner.get().withProgress(this) {
            scan(scanOptions)
        }

        log(TAG, INFO) { "performScan(): ${results.size} clusters found" }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.totalSize }
        log(TAG) { "Field warm up done." }

        val data = Data(clusters = results)
        internalData.value = data

        return DeduplicatorScanTask.Success(
            itemCount = data.clusters.size,
            recoverableSpace = data.redundantSize,
        )
    }

    private suspend fun performDelete(
        task: DeduplicatorDeleteTask = DeduplicatorDeleteTask()
    ): DeduplicatorDeleteTask.Success {
        log(TAG) { "performDelete(): $task" }

        val snapshot = internalData.value!!

        val result = deleter.get().withProgress(this) { delete(task, snapshot) }

        updateProgress { Progress.Data() }

        internalData.value = snapshot.prune(result.success.map { it.identifier }.toSet(), arbiter)

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

        val strategy = arbiter.getStrategy()
        val newGroups = cluster.groups.mapNotNull { group ->
            val filteredDuplicates = group.duplicates.filter { !paths.contains(it.path) }
            val keeperValid = filteredDuplicates.any { it.identifier == group.keeperIdentifier }
            val newGroup = when (group.type) {
                Duplicate.Type.CHECKSUM -> {
                    val dupes = filteredDuplicates.filterIsInstance<ChecksumDuplicate>().toSet()
                    val keeperId = when {
                        keeperValid -> group.keeperIdentifier
                        dupes.size >= 2 -> arbiter.decideDuplicates(dupes, strategy).first.identifier
                        else -> null
                    }
                    (group as ChecksumDuplicate.Group).copy(duplicates = dupes, keeperIdentifier = keeperId)
                }

                Duplicate.Type.PHASH -> {
                    val dupes = filteredDuplicates.filterIsInstance<PHashDuplicate>().toSet()
                    val keeperId = when {
                        keeperValid -> group.keeperIdentifier
                        dupes.size >= 2 -> arbiter.decideDuplicates(dupes, strategy).first.identifier
                        else -> null
                    }
                    (group as PHashDuplicate.Group).copy(duplicates = dupes, keeperIdentifier = keeperId)
                }

                Duplicate.Type.MEDIA -> {
                    val dupes = filteredDuplicates.filterIsInstance<MediaDuplicate>().toSet()
                    val keeperId = when {
                        keeperValid -> group.keeperIdentifier
                        dupes.size >= 2 -> arbiter.decideDuplicates(dupes, strategy).first.identifier
                        else -> null
                    }
                    (group as MediaDuplicate.Group).copy(duplicates = dupes, keeperIdentifier = keeperId)
                }
            }
            if (newGroup.duplicates.size >= 2) {
                newGroup
            } else {
                log(TAG) { "Group is less than 2 entries: $newGroup" }
                null
            }
        }.toSet()

        val favoriteValid = newGroups.any { it.identifier == cluster.favoriteGroupIdentifier }
        val newFavoriteId = when {
            favoriteValid -> cluster.favoriteGroupIdentifier
            newGroups.size >= 2 -> arbiter.decideGroups(newGroups, strategy).first.identifier
            else -> newGroups.firstOrNull()?.identifier
        }

        val newCluster: Duplicate.Cluster? = cluster
            .copy(groups = newGroups, favoriteGroupIdentifier = newFavoriteId)
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
    ): ExclusionUndo = toolLock.withLock {
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
        val saved = exclusionManager.save(exclusions)

        val updated = snapshot.copy(
            clusters = snapshot.clusters.filter { !identifiers.contains(it.identifier) }.toSet()
        )
        internalData.value = updated

        ExclusionUndo(
            exclusionIds = saved.map { it.id }.toSet(),
            previousData = snapshot,
            postExcludeData = updated,
        )
    }

    suspend fun undoExclude(handle: ExclusionUndo) = toolLock.withLock {
        log(TAG, INFO) { "undoExclude(${handle.exclusionIds})" }
        if (handle.exclusionIds.isNotEmpty()) {
            exclusionManager.remove(handle.exclusionIds)
        }
        if (internalData.value === handle.postExcludeData) {
            internalData.value = handle.previousData
        } else {
            log(TAG, WARN) { "undoExclude: state moved on, only removed exclusions" }
        }
    }

    data class ExclusionUndo(
        val exclusionIds: Set<ExclusionId>,
        internal val previousData: Data,
        internal val postExcludeData: Data,
    )

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val lastResult: DeduplicatorTask.Result? = null,
    ) : SDMTool.State

    data class Data(
        val clusters: Set<Duplicate.Cluster> = emptySet(),
    ) {
        val redundantSize: Long get() = clusters.sumOf { it.redundantSize }
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

internal suspend fun Deduplicator.Data.prune(
    deletedIds: Set<Duplicate.Id>,
    arbiter: DuplicatesArbiter,
): Deduplicator.Data {
    val strategy = arbiter.getStrategy()
    val newClusters = this.clusters
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
                    val keeperValid = newDuplicates.any { it.identifier == oldGroup.keeperIdentifier }
                    when (oldGroup.type) {
                        Duplicate.Type.CHECKSUM -> {
                            oldGroup as ChecksumDuplicate.Group
                            @Suppress("UNCHECKED_CAST")
                            val dupes = newDuplicates as Set<ChecksumDuplicate>
                            val keeperId = when {
                                keeperValid -> oldGroup.keeperIdentifier
                                dupes.size >= 2 -> arbiter.decideDuplicates(dupes, strategy).first.identifier
                                else -> null
                            }
                            oldGroup.copy(duplicates = dupes, keeperIdentifier = keeperId)
                        }

                        Duplicate.Type.PHASH -> {
                            oldGroup as PHashDuplicate.Group
                            @Suppress("UNCHECKED_CAST")
                            val dupes = newDuplicates as Set<PHashDuplicate>
                            val keeperId = when {
                                keeperValid -> oldGroup.keeperIdentifier
                                dupes.size >= 2 -> arbiter.decideDuplicates(dupes, strategy).first.identifier
                                else -> null
                            }
                            oldGroup.copy(duplicates = dupes, keeperIdentifier = keeperId)
                        }

                        Duplicate.Type.MEDIA -> {
                            oldGroup as MediaDuplicate.Group
                            @Suppress("UNCHECKED_CAST")
                            val dupes = newDuplicates as Set<MediaDuplicate>
                            val keeperId = when {
                                keeperValid -> oldGroup.keeperIdentifier
                                dupes.size >= 2 -> arbiter.decideDuplicates(dupes, strategy).first.identifier
                                else -> null
                            }
                            oldGroup.copy(duplicates = dupes, keeperIdentifier = keeperId)
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
            val favoriteValid = newGroups.any { it.identifier == oldCluster.favoriteGroupIdentifier }
            val newFavoriteId = when {
                favoriteValid -> oldCluster.favoriteGroupIdentifier
                newGroups.size >= 2 -> arbiter.decideGroups(newGroups, strategy).first.identifier
                else -> newGroups.firstOrNull()?.identifier
            }
            oldCluster.copy(groups = newGroups, favoriteGroupIdentifier = newFavoriteId)
        }
        .filter {
            val isSolo = it.count < 2
            if (isSolo) log(Deduplicator.TAG) { "Prune: Cluster only has one item: $it" }
            !isSolo
        }
        .toSet()

    return this.copy(clusters = newClusters)
}