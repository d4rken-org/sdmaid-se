package eu.darken.sdmse.appcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
    private val appScannerProvider: Provider<AppScanner>,
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

    override val type: SDMTool.Type = SDMTool.Type.APPCLEANER

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AppCleanerTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is AppCleanerScanTask -> performScan(task)
                    is AppCleanerDeleteTask -> performDelete(task)
                }
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: AppCleanerScanTask): AppCleanerTask.Result = try {
        log(TAG, VERBOSE) { "performScan($task)" }

        val scanStart = System.currentTimeMillis()
        internalData.value = null

        val scanner = appScannerProvider.get()

        scanner.initialize()

        val results = scanner.withProgress(this) {
            scan()
        }

        internalData.value = Data(
            junks = results,
        )

        val scanStop = System.currentTimeMillis()
        val time = Duration.ofMillis(scanStop - scanStart)
        AppCleanerScanTask.Success(
            duration = time
        )
    } catch (e: Exception) {
        log(TAG, ERROR) { "performScan($task) failed: ${e.asLog()}" }
        AppCleanerScanTask.Error(e)
    }

    private suspend fun performDelete(task: AppCleanerDeleteTask): AppCleanerTask.Result = try {
        log(TAG, VERBOSE) { "performDelete($task)" }

        AppCleanerDeleteTask.Success(TODO())
    } catch (e: Exception) {
        log(TAG, ERROR) { "performScan($task) failed: ${e.asLog()}" }
        AppCleanerDeleteTask.Error(e)
    }

    data class Data(
        val junks: Collection<AppJunk>
    ) {
        val totalSize: Long
            get() = junks.sumOf { it.size }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppCleaner): SDMTool
    }

    companion object {
        private val TAG = logTag("AppCleaner")
    }
}