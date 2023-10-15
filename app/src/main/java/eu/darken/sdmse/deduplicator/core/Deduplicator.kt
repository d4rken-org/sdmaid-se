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
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.tasks.*
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.deduplicator.core.types.Duplicate
import eu.darken.sdmse.deduplicator.core.types.HashDuplicate
import eu.darken.sdmse.exclusion.core.*
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Deduplicator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val filterFactories: Set<@JvmSuppressWildcards CorpseFilter.Factory>,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    settings: DeduplicatorSettings,
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

        val pathExclusions = exclusionManager.pathExclusions(SDMTool.Type.DEDUPLICATOR)

        val results: Collection<Duplicate.Group> = setOf(
            HashDuplicate.Group(
                identifier = Duplicate.Group.Identifier("TestId1"),
                duplicates = LocalPath
                    .build("/storage/emulated/0/DCIM/Camera")
                    .lookupFiles(gatewaySwitch)
                    .map {
                        HashDuplicate(
                            lookup = it
                        )
                    }
            ),
            HashDuplicate.Group(
                identifier = Duplicate.Group.Identifier("TestId2"),
                duplicates = LocalPath.build("/storage/emulated/0/Download")
                    .lookupFiles(gatewaySwitch)
                    .map {
                        HashDuplicate(
                            lookup = it
                        )
                    }
            ),
        )

        results.forEach { log(TAG, INFO) { "Result: $it" } }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.size }
        log(TAG) { "Field warm up done." }

        internalData.value = Data(
            groups = results
        )

        return DeduplicatorScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.size },
        )
    }

    suspend fun exclude(groups: Set<Duplicate.Group.Identifier>): Unit = toolLock.withLock {
        log(TAG) { "exclude(): $groups" }

        TODO()
//        val snapshot = internalData.value!!

//        val targets = snapshot.corpses
//            .filter { identifiers.contains(it.identifier) }
//
//        val exclusions = targets.map {
//            PathExclusion(
//                path = it.lookup.lookedUp,
//                tags = setOf(Exclusion.Tag.CORPSEFINDER),
//            )
//        }.toSet()
//        exclusionManager.save(exclusions)
//
//        internalData.value = snapshot.copy(
//            corpses = snapshot.corpses.filter { corpse ->
//                exclusions.none { it.match(corpse.lookup) }
//            }
//        )
    }

    suspend fun exclude(group: Duplicate.Group.Identifier, files: Collection<APath>): Unit = toolLock.withLock {
        log(TAG) { "exclude(): $group - $files" }

        TODO()
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
    ) : SDMTool.State

    data class Data(
        val groups: Collection<Duplicate.Group>,
        val lastResult: DeduplicatorTask.Result? = null,
    ) {
        val totalSize: Long get() = groups.sumOf { it.size }
        val totalCount: Int get() = groups.size
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