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
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.deleteAll
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.filter.getLabel
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerFileDeleteTask
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

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
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
                    is SystemCleanerFileDeleteTask -> performFileDelete(task)
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

        val deleted = mutableSetOf<FilterContent>()
        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val targets = task.toDelete ?: snapshot.filterContents.map { it.filterIdentifier }

        targets.forEach { target ->
            val filterContent = snapshot.filterContents.single { it.filterIdentifier == target }

            updateProgressPrimary(caString {
                it.getString(R.string.general_progress_deleting, filterContent.filterIdentifier.getLabel(it))
            })

            log(TAG) { "Deleting $target..." }
            filterContent.items.forEach { contentPath ->
                contentPath.deleteAll(gatewaySwitch) {
                    updateProgressSecondary(it.userReadablePath)
                    true
                }
            }
            log(TAG) { "Deleted $target!" }

            deleted.add(filterContent)
        }

        updateProgressPrimary(R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        internalData.value = snapshot.copy(
            filterContents = snapshot.filterContents.minus(deleted)
        )

        return SystemCleanerDeleteTask.Success(
            deletedItems = deleted.size,
            recoveredSpace = deleted.sumOf { it.size }
        )
    }

    private suspend fun performFileDelete(task: SystemCleanerFileDeleteTask): SystemCleanerTask.Result {
        log(TAG, VERBOSE) { "performDelete(): $task" }

        val deleted = mutableSetOf<APathLookup<*>>()
        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val filter = snapshot.filterContents.single { it.filterIdentifier == task.identifier }

        updateProgressPrimary(caString {
            it.getString(R.string.general_progress_deleting, filter.filterIdentifier.getLabel(it))
        })

        task.toDelete.forEach { target ->
            log(TAG) { "Deleting $target..." }
            val filterItem = filter.items.single { it.path == target.path }
            filterItem.deleteAll(gatewaySwitch) {
                updateProgressSecondary(it.userReadablePath)
                true
            }
            log(TAG) { "Deleted $target!" }

            deleted.add(filterItem)
        }

        updateProgressPrimary(R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        val updatedContent = filter.copy(
            items = filter.items.minus(deleted)
        )

        internalData.value = if (updatedContent.items.isEmpty()) {
            snapshot.copy(
                filterContents = snapshot.filterContents.minus(filter)
            )
        } else {
            snapshot.copy(
                filterContents = snapshot.filterContents.minus(filter).plus(updatedContent)
            )
        }

        return SystemCleanerDeleteTask.Success(
            deletedItems = deleted.size,
            recoveredSpace = deleted.sumOf { it.size }
        )
    }

    data class Data(
        val filterContents: Collection<FilterContent>
    ) {
        val totalSize: Long
            get() = filterContents.sumOf { it.size }
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