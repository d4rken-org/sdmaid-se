package eu.darken.sdmse.analyzer.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.analyzer.core.storage.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.DeviceStorageScanTask
import eu.darken.sdmse.analyzer.core.storage.DeviceStorageScanner
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Analyzer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val storageScanner: Provider<DeviceStorageScanner>,
) : SDMTool, Progress.Client {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val deviceStorageData = MutableStateFlow(emptySet<DeviceStorage>())
    private val storageContentData = MutableStateFlow(emptySet<DeviceStorage>())
    val data: Flow<Data> = combine(
        deviceStorageData,
        storageContentData,
    ) { storages, contents ->
        Data(
            storages = storages,
        )
    }

    override val type: SDMTool.Type = SDMTool.Type.ANALYZER

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AnalyzerTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = when (task) {
                is DeviceStorageScanTask -> performScan(task)
                else -> throw UnsupportedOperationException("Unsupported task: $task")
            }

            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: DeviceStorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }

        deviceStorageData.value = emptySet()

        val scanner = storageScanner.get()
        val deviceStorages = scanner.scan()

        deviceStorageData.value = deviceStorages

        return DeviceStorageScanTask.Result(itemCount = deviceStorages.size)
    }


    data class Data(
        val storages: Set<DeviceStorage> = emptySet()
    )

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Analyzer): SDMTool
    }

    companion object {
        private val TAG = logTag("Analyzer")
    }
}