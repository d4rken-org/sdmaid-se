package eu.darken.sdmse.corpsefinder.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorpseFinder @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val filterFactories: Set<@JvmSuppressWildcards CorpseFilter.Factory>,
    fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.CORPSEFINDER

    private val usedResources = setOf(fileForensics, gatewaySwitch, pkgOps)
    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as CorpseFinderTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgressPrimary(R.string.general_progress_loading)
        updateProgressSecondary(easterEggProgressMsg)
        updateProgressCount(Progress.Count.Indeterminate())

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is CorpseFinderScanTask -> performScan(task)
                    is CorpseFinderDeleteTask -> deleteCorpses(task)
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

    private suspend fun performScan(task: CorpseFinderScanTask): CorpseFinderTask.Result {
        log(TAG) { "performScan(): $task" }

        internalData.value = null

        val filters = filterFactories
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { log(TAG) { "Created filter: $it" } }

        val results = filters
            .map { filter ->
                filter
                    .withProgress(this@CorpseFinder) { scan() }
                    .also { log(TAG) { "$filter found ${it.size} corpses" } }
            }
            .flatten()

        results.forEach { log(TAG, INFO) { "Result: $it" } }

        internalData.value = Data(
            corpses = results
        )

        return CorpseFinderScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.size },
        )
    }

    private suspend fun deleteCorpses(task: CorpseFinderDeleteTask): CorpseFinderTask.Result {
        log(TAG) { "deleteCorpses(): $task" }

        val deletedCorpses = mutableSetOf<Corpse>()
        val deletedContents = mutableMapOf<Corpse, Set<APathLookup<*>>>()
        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val targetCorpses = task.targetCorpses ?: snapshot.corpses.map { it.path }
        targetCorpses.forEach { targetCorpse ->
            val corpse = snapshot.corpses.single { it.path.matches(targetCorpse) }

            if (!task.targetContent.isNullOrEmpty()) {
                val deleted = mutableSetOf<APathLookup<*>>()

                task.targetContent.forEach { targetContent ->
                    updateProgressPrimary(caString {
                        it.getString(R.string.general_progress_deleting, targetContent.userReadableName.get(it))
                    })
                    log(TAG) { "Deleting $targetContent..." }
                    targetContent.deleteAll(gatewaySwitch) {
                        updateProgressSecondary(it.userReadablePath)
                        true
                    }
                    log(TAG) { "Deleted $targetContent!" }
                    deleted.addAll(
                        corpse.content.filter { targetContent.isAncestorOf(it) || targetContent.matches(it) }
                    )
                }

                deletedContents[corpse] = deleted
            } else {
                updateProgressPrimary(caString {
                    it.getString(R.string.general_progress_deleting, corpse.path.userReadableName.get(it))
                })
                log(TAG) { "Deleting $targetCorpse..." }
                corpse.path.deleteAll(gatewaySwitch) {
                    updateProgressSecondary(it.userReadablePath)
                    true
                }
                log(TAG) { "Deleted $targetCorpse!" }
                deletedCorpses.add(corpse)
            }
        }

        updateProgressPrimary(R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        internalData.value = snapshot.copy(
            corpses = snapshot.corpses
                .mapNotNull { corpse ->
                    when {
                        deletedCorpses.contains(corpse) -> null
                        deletedContents.containsKey(corpse) -> corpse.copy(
                            content = corpse.content.filter { c -> deletedContents[corpse]!!.none { it.matches(c) } }
                        )
                        else -> corpse
                    }
                }
        )

        return CorpseFinderDeleteTask.Success(
            deletedItems = deletedCorpses.size + deletedContents.values.sumOf { it.size },
            recoveredSpace = deletedCorpses.sumOf { it.size } + deletedContents.values.sumOf { contents -> contents.sumOf { it.size } }
        )
    }

    data class Data(
        val corpses: Collection<Corpse>,
        val lastResult: CorpseFinderTask.Result? = null,
    ) {
        val totalSize: Long get() = corpses.sumOf { it.size }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: CorpseFinder): SDMTool
    }

    companion object {
        private val TAG = logTag("CorpseFinder")
    }
}