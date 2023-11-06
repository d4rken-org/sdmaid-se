package eu.darken.sdmse.deduplicator.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.corpsefinder.core.tasks.*
import eu.darken.sdmse.deduplicator.core.deleter.DuplicatesDeleter
import eu.darken.sdmse.deduplicator.core.scanner.DuplicatesScanner
import eu.darken.sdmse.deduplicator.core.scanner.Sleuth
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.exclusion.core.*
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Deduplicator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val sleuthFactories: Set<@JvmSuppressWildcards Sleuth.Factory>,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val settings: DeduplicatorSettings,
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
        updateProgress { Progress.DEFAULT_STATE }

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is DeduplicatorScanTask -> performScan(task)
                    is DeduplicatorDeleteTask -> performDelete(task)
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

    private suspend fun performScan(task: DeduplicatorScanTask): DeduplicatorScanTask.Result {
        log(TAG) { "performScan(): $task" }

        internalData.value = null

        val sleuths = sleuthFactories
            .asFlow()
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { log(TAG) { "Sleuth created: $it" } }
            .toList()

        val results = scanner.get().withProgress(this) {
            scan(sleuths)
        }

        log(TAG, INFO) { "performScan():${results.size} clusters found" }

        results.forEach { c ->
            log(TAG, VERBOSE) { "performScan(): Cluster ${c.identifier}: ${c.groups.size} groups, ${c.count} dupes" }
            c.groups.forEach { g ->
                log(TAG, VERBOSE) { "performScan():  Group ${g.identifier}: ${g.duplicates.size} dupes" }
                g.duplicates.forEach { d ->
                    log(TAG, VERBOSE) { "performScan():   Duplicate: $d" }
                }
            }
        }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.totalSize }
        log(TAG) { "Field warm up done." }

        internalData.value = Data(
            clusters = results
        )

        return DeduplicatorScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.totalSize },
        )
    }

    private suspend fun performDelete(task: DeduplicatorDeleteTask): DeduplicatorDeleteTask.Result {
        log(TAG) { "performDelete(): $task" }

        val snapshot = internalData.value!!

        val result = deleter.get().delete(task, snapshot)
        val pruneResult = snapshot.prune(result)
        internalData.value = pruneResult.newData

        return DeduplicatorDeleteTask.Success(
            deletedItems = pruneResult.count,
            recoveredSpace = pruneResult.freed,
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

                        Duplicate.Type.PHASH -> {
                            group // TODO NOOP
                        }
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

data class PruneResult(
    val newData: Deduplicator.Data,
    val removed: Collection<Duplicate>,
    val freed: Long,
) {
    val count: Int
        get() = removed.size
}

internal fun Deduplicator.Data.prune(deleter: DuplicatesDeleter.Deleted): PruneResult {
    val removedDupes = mutableSetOf<Duplicate>()
    val newClusters = this.clusters
        .asSequence()
        .map { oldCluster ->
            // Remove duplicates from groups
            val newGroups = oldCluster.groups
                .map { oldGroup ->
                    val newDuplicates: Set<Duplicate> = oldGroup.duplicates
                        .filter { toDelete ->
                            val wasDeleted = deleter.success.contains(toDelete.identifier)
                            if (wasDeleted) {
                                log(Deduplicator.TAG) { "Prune: Deleted duplicate: $toDelete" }
                                removedDupes.add(toDelete)
                            }
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
                    val solo = it.duplicates.size < 2
                    val empty = it.duplicates.isEmpty()
                    if (empty) log(Deduplicator.TAG) { "Prune: Empty group: $it" }
                    if (solo) log(Deduplicator.TAG) { "Prune: Solo group: $it" }
                    !solo && !empty
                }
                .toSet()
            oldCluster.copy(groups = newGroups)
        }
        .filter {
            val clusterEmpty = it.groups.isEmpty()
            if (clusterEmpty) log(Deduplicator.TAG) { "Prune: Empty cluster: $it" }
            !clusterEmpty
        }
        .toSet()

    return PruneResult(
        newData = this.copy(clusters = newClusters),
        removed = removedDupes,
        freed = removedDupes.sumOf { it.size }
    )
}