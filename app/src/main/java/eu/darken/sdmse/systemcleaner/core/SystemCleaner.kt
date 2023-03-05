package eu.darken.sdmse.systemcleaner.core

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
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SystemCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
    private val crawlerProvider: Provider<SystemCrawler>,
    private val exclusionManager: ExclusionManager,
    pkgOps: PkgOps,
) : SDMTool, Progress.Client {

    private val usedResources = setOf(fileForensics, gatewaySwitch, pkgOps)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    override val type: SDMTool.Type = SDMTool.Type.SYSTEMCLEANER

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as SystemCleanerTask
        log(TAG) { "submit($task) starting..." }
        updateProgressPrimary(R.string.general_progress_loading)
        updateProgressSecondary(easterEggProgressMsg)
        updateProgressCount(Progress.Count.Indeterminate())

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is SystemCleanerScanTask -> performScan(task)
                    is SystemCleanerDeleteTask -> performDelete(task)
                }
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: SystemCleanerScanTask): SystemCleanerTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }
        updateProgressPrimary(R.string.general_progress_searching)

        internalData.value = null

        val crawler = crawlerProvider.get()

        val results = crawler.withProgress(this) {
            crawl()
        }

        internalData.value = Data(
            filterContents = results
        )

        return SystemCleanerScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.size },
        )
    }

    private suspend fun performDelete(task: SystemCleanerDeleteTask): SystemCleanerTask.Result {
        log(TAG, VERBOSE) { "performDelete(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val deletedContents = mutableMapOf<FilterContent, Set<APathLookup<*>>>()

        val targetFilters = task.targetFilters ?: snapshot.filterContents.map { it.filterIdentifier }
        targetFilters.forEach { targetIdentifier ->
            val filterContent = snapshot.filterContents.single { it.filterIdentifier == targetIdentifier }

            val deleted = mutableSetOf<APathLookup<*>>()
            val targetContents = task.targetContent ?: filterContent.items
            targetContents.forEach { targetContent ->
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
                    filterContent.items.filter { targetContent.isAncestorOf(it) || targetContent.matches(it) }
                )
            }

            deletedContents[filterContent] = deleted
        }

        updateProgressPrimary(R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        internalData.value = snapshot.copy(
            filterContents = snapshot.filterContents
                .map { filterContent ->
                    when {
                        deletedContents.containsKey(filterContent) -> filterContent.copy(
                            items = filterContent.items.filter { c ->
                                deletedContents[filterContent]!!.none { it.matches(c) }
                            }
                        )
                        else -> filterContent
                    }
                }
                .filter { it.items.isNotEmpty() }
        )

        return SystemCleanerDeleteTask.Success(
            deletedItems = deletedContents.values.sumOf { it.size },
            recoveredSpace = deletedContents.values.sumOf { contents -> contents.sumOf { it.size } }
        )
    }

    suspend fun exclude(identifier: FilterIdentifier, target: APath) = toolLock.withLock {
        log(TAG) { "exclude(): $identifier, $target" }
        val exclusion = PathExclusion(
            path = target.downCast(),
            tags = setOf(Exclusion.Tag.SYSTEMCLEANER),
        )
        exclusionManager.add(exclusion)

        val snapshot = internalData.value!!
        internalData.value = snapshot.copy(
            filterContents = snapshot.filterContents
                .map { fc ->
                    fc.copy(items = fc.items.filter {
                        val hit = it.matches(target)
                        if (hit) log(TAG) { "exclude(): Excluded $it" }
                        !hit
                    })
                }
                .filter { it.items.isNotEmpty() }
        )
    }

    data class Data(
        val filterContents: Collection<FilterContent>
    ) {
        val totalSize: Long get() = filterContents.sumOf { it.size }
        val totalCount: Int get() = filterContents.sumOf { it.items.size }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SystemCleaner): SDMTool
    }

    companion object {
        private val TAG = logTag("SystemCleaner")
    }
}