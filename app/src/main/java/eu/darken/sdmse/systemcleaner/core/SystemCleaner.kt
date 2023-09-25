package eu.darken.sdmse.systemcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.excludeNestedLookups
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.FilterSource
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
    private val crawler: SystemCrawler,
    private val exclusionManager: ExclusionManager,
    private val filterSource: FilterSource,
    pkgOps: PkgOps,
    private val rootManager: RootManager,
) : SDMTool, Progress.Client {

    private val usedResources = setOf(fileForensics, gatewaySwitch, pkgOps)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)

    override val type: SDMTool.Type = SDMTool.Type.SYSTEMCLEANER

    override val state: Flow<State> = combine(
        internalData,
        progress,
        rootManager.useRoot,
    ) { data, progress, useRoot ->
        State(
            data = data,
            progress = progress,
            areSystemFilterAvailable = useRoot,
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as SystemCleanerTask
        log(TAG) { "submit($task) starting..." }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(easterEggProgressMsg)
        updateProgressCount(Progress.Count.Indeterminate())

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is SystemCleanerScanTask -> performScan(task)
                    is SystemCleanerDeleteTask -> performDelete(task)
                    is SystemCleanerSchedulerTask -> {
                        performScan(SystemCleanerScanTask())
                        performDelete(SystemCleanerDeleteTask())
                    }
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
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)

        internalData.value = null

        val results = crawler.withProgress(this) {
            crawl(filterSource.create())
        }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.size }
        log(TAG) { "Field warm up done." }

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

        val deletedContents = mutableMapOf<FilterContent, Set<APath>>()

        val targetFilters = task.targetFilters ?: snapshot.filterContents.map { it.identifier }
        targetFilters.forEach { targetIdentifier ->
            val filterContent = snapshot.filterContents.single { it.identifier == targetIdentifier }
            updateProgressPrimary(caString { filterContent.label.get(it) })

            val deleted = mutableSetOf<APath>()
            val targetContents = task.targetContent ?: filterContent.items.map { it.lookedUp }
            targetContents
                .filterDistinctRoots()
                .forEach { targetContent ->
                    log(TAG) { "Deleting $targetContent..." }
                    updateProgressSecondary(targetContent.userReadablePath)
                    try {
                        targetContent.deleteAll(gatewaySwitch)
                        log(TAG) { "Deleted $targetContent!" }
                        deleted.add(targetContent)
                    } catch (e: WriteException) {
                        log(TAG, WARN) { "Deletion failed for $targetContent" }
                    }
                }

            deletedContents[filterContent] = deleted
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        var deletedContentSize = 0L
        var deletedContentCount = 0

        internalData.value = snapshot.copy(
            filterContents = snapshot.filterContents
                .map { filterContent ->
                    when {
                        deletedContents.containsKey(filterContent) -> filterContent.copy(
                            items = filterContent.items.filter { contentItem ->
                                val isDeleted = deletedContents[filterContent]!!.any { deleted ->
                                    deleted.isAncestorOf(contentItem) || deleted.matches(contentItem)
                                }
                                if (isDeleted) {
                                    deletedContentSize += contentItem.size
                                    deletedContentCount++
                                }
                                !isDeleted
                            }
                        )

                        else -> filterContent
                    }
                }
                .filter { it.items.isNotEmpty() }
        )

        return SystemCleanerDeleteTask.Success(
            deletedItems = deletedContentCount,
            recoveredSpace = deletedContentSize
        )
    }

    suspend fun exclude(identifier: FilterIdentifier, exclusionTargets: Set<APath>) = toolLock.withLock {
        log(TAG) { "exclude(): $identifier, ${exclusionTargets.size}" }
        val exclusions = exclusionTargets.map {
            PathExclusion(
                path = it,
                tags = setOf(Exclusion.Tag.SYSTEMCLEANER),
            )
        }.toSet()
        exclusionManager.save(exclusions)

        val snapshot = internalData.value!!
        internalData.value = snapshot.copy(
            filterContents = snapshot.filterContents
                .map { it.copy(items = exclusions.excludeNestedLookups(it.items)) }
                .filter { it.items.isNotEmpty() }
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val areSystemFilterAvailable: Boolean,
    ) : SDMTool.State

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