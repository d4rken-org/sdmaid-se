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
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.corpsefinder.core.tasks.*
import eu.darken.sdmse.deduplicator.core.scanner.DuplicatesScanner
import eu.darken.sdmse.deduplicator.core.scanner.Sleuth
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
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
import javax.inject.Singleton

@Singleton
class Deduplicator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val sleuthFactories: Set<@JvmSuppressWildcards Sleuth.Factory>,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val settings: DeduplicatorSettings,
    private val scanner: DuplicatesScanner,
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

    override val state: Flow<State> = combine(
        internalData,
        progress,
    ) { data, progress ->
        State(
            data = data,
            progress = progress,
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as DeduplicatorTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(easterEggProgressMsg)
        updateProgressCount(Progress.Count.Indeterminate())

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is DeduplicatorScanTask -> performScan(task)
                    is DeduplicatorDeleteTask -> TODO()
                }
            }
            internalData.value = internalData.value?.copy(
                lastResult = result,
            )
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

        val results = scanner.withProgress(this) {
            scan(sleuths)
        }

        log(TAG, INFO) { "${results.size} clusters found" }
        results.forEach { log(TAG) { "Result: $it" } }

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

    suspend fun exclude(
        identifier: Duplicate.Cluster.Identifier,
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
                    val newGroup = when (group) {
                        is ChecksumDuplicate.Group -> group.copy(
                            duplicates = group.duplicates.filter { !paths.contains(it.path) }
                        )

                        else -> throw NotImplementedError("Unsupported group $group")
                    }
                    if (newGroup.duplicates.size >= 2) {
                        newGroup
                    } else {
                        log(TAG) { "Group is less than 2 entries: $newGroup" }
                        null
                    }
                }
            )
            .takeIf { it.groups.isNotEmpty() }

        if (newCluster == null) log(TAG) { "Cluster was empty after exclusion" }

        internalData.value = snapshot.copy(
            clusters = snapshot.clusters.mapNotNull { oldCluster ->
                if (oldCluster.identifier == identifier) newCluster else oldCluster
            }
        )
    }

    suspend fun exclude(
        identifiers: Collection<Duplicate.Cluster.Identifier>
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
            clusters = snapshot.clusters.filter { !identifiers.contains(it.identifier) }
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
    ) : SDMTool.State

    data class Data(
        val clusters: Collection<Duplicate.Cluster>,
        val lastResult: DeduplicatorTask.Result? = null,
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
        private val TAG = logTag("Deduplicator")
    }
}