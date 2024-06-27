package eu.darken.sdmse.systemcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.FilterSource
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.excludeNestedLookups
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    gatewaySwitch: GatewaySwitch,
    private val crawler: SystemCrawler,
    private val exclusionManager: ExclusionManager,
    private val filterSource: FilterSource,
    pkgOps: PkgOps,
    rootManager: RootManager,
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
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is SystemCleanerScanTask -> performScan(task)
                    is SystemCleanerProcessingTask -> performProcessing(task)
                    is SystemCleanerSchedulerTask -> {
                        performScan()
                        performProcessing().let {
                            SystemCleanerSchedulerTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths
                            )
                        }
                    }

                    is SystemCleanerOneClickTask -> {
                        performScan()
                        performProcessing().let {
                            SystemCleanerOneClickTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths
                            )
                        }
                    }
                }
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(
        task: SystemCleanerScanTask = SystemCleanerScanTask()
    ): SystemCleanerScanTask.Success {
        log(TAG, VERBOSE) { "performScan(): $task" }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)

        internalData.value = null

        val results = crawler.withProgress(this) {
            crawl(filterSource.create(onlyEnabled = true))
        }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.size }
        log(TAG) { "Field warm up done." }

        internalData.value = Data(
            filterContents = results
        )

        return SystemCleanerScanTask.Success(
            itemCount = results.sumOf { it.items.size },
            recoverableSpace = results.sumOf { it.size },
        )
    }

    private suspend fun performProcessing(
        task: SystemCleanerProcessingTask = SystemCleanerProcessingTask()
    ): SystemCleanerProcessingTask.Success {
        log(TAG, VERBOSE) { "performProcessing(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val processedContents = mutableMapOf<FilterContent, Set<SystemCleanerFilter.Match>>()

        val targetFilters = task.targetFilters ?: snapshot.filterContents.map { it.identifier }
        val filters = filterSource.create(onlyEnabled = false)

        targetFilters.forEach { targetIdentifier ->
            val filterContent = snapshot.filterContents.single { it.identifier == targetIdentifier }
            updateProgressPrimary(caString { filterContent.label.get(it) })

            val filter = filters.singleOrNull { it.identifier == targetIdentifier }
                ?: throw IllegalStateException("No filter matches $targetIdentifier")

            val processed = mutableSetOf<SystemCleanerFilter.Match>()
            val targetMatches = filterContent.items.filter {
                task.targetContent == null || task.targetContent.contains(it.path)
            }

            filter.withProgress(
                client = this,
                onUpdate = { old, new ->
                    old?.copy(
                        secondary = new?.primary ?: CaString.EMPTY,
                        count = new?.count ?: Progress.Data().count,
                    )
                },
            ) {
                try {
                    process(targetMatches)
                    log(TAG) { "Processed ${targetMatches.size} for ${filter.identifier}!" }
                    processed.addAll(targetMatches)
                } catch (e: PathException) {
                    log(TAG, ERROR) { "Failed to process for ${filter.identifier}: ${e.asLog()}" }
                }
            }

            processedContents[filterContent] = processed
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        var gainedContentSize = 0L
        val processedContent = mutableSetOf<APath>()

        internalData.value = snapshot.copy(
            filterContents = snapshot.filterContents
                .map { filterContent ->
                    when {
                        processedContents.containsKey(filterContent) -> filterContent.copy(
                            items = filterContent.items.filter { contentItem ->
                                val isProcessed = processedContents[filterContent]!!.any { processed ->
                                    processed.path.isAncestorOf(contentItem.path) || processed.path.matches(contentItem.path)
                                }
                                if (isProcessed) {
                                    gainedContentSize += contentItem.expectedGain
                                    processedContent.add(contentItem.path)
                                }
                                !isProcessed
                            }
                        )

                        else -> filterContent
                    }
                }
                .filter { it.items.isNotEmpty() }
        )

        return SystemCleanerProcessingTask.Success(
            affectedSpace = gainedContentSize,
            affectedPaths = processedContent,
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