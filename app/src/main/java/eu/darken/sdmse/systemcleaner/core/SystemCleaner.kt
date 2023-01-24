package eu.darken.sdmse.systemcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
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
                }
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: SystemCleanerScanTask): SystemCleanerTask.Result = try {
        log(TAG, VERBOSE) { "performScan($task)" }
        updateProgressPrimary(R.string.general_progress_searching)

        val scanStart = System.currentTimeMillis()
        internalData.value = null

        val crawler = crawlerProvider.get()

        val results = crawler.withProgress(this) {
            crawl()
        }

        internalData.value = Data(
            filterContents = results
        )

        val scanStop = System.currentTimeMillis()
        val time = Duration.ofMillis(scanStop - scanStart)
        SystemCleanerScanTask.Success(
            duration = time
        )
    } catch (e: Exception) {
        log(TAG, ERROR) { "performScan($task) failed: ${e.asLog()}" }
        SystemCleanerScanTask.Error(e)
    }

    private suspend fun performDelete(task: SystemCleanerDeleteTask): SystemCleanerTask.Result = try {
        log(TAG, VERBOSE) { "performDelete($task)" }

        SystemCleanerDeleteTask.Success(TODO())
    } catch (e: Exception) {
        log(TAG, ERROR) { "performScan($task) failed: ${e.asLog()}" }
        SystemCleanerDeleteTask.Error(e)
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