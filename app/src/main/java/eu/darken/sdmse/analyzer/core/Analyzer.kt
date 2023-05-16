package eu.darken.sdmse.analyzer.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.analyzer.core.content.StorageContentScanTask
import eu.darken.sdmse.analyzer.core.content.StorageContentScanner
import eu.darken.sdmse.analyzer.core.content.types.StorageContent
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanner
import eu.darken.sdmse.common.collections.mutate
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
    private val deviceScanner: Provider<DeviceStorageScanner>,
    private val storageScanner: Provider<StorageContentScanner>,
) : SDMTool, Progress.Client {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val storagesDevice = MutableStateFlow(emptySet<DeviceStorage>())
    private val storageContents = MutableStateFlow(emptyMap<DeviceStorage.Id, Collection<StorageContent>>())
    val data: Flow<Data> = combine(
        storagesDevice,
        storageContents,
    ) { storages, contents ->
        Data(
            storages = storages,
            contents = contents,
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
                is DeviceStorageScanTask -> scanStorageDevices(task)
                is StorageContentScanTask -> scanStorageContents(task)
                else -> throw UnsupportedOperationException("Unsupported task: $task")
            }

            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun scanStorageDevices(task: DeviceStorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageDevices(): $task" }

        storagesDevice.value = emptySet()

        val scanner = deviceScanner.get()
        val storages = scanner.scan()

        storagesDevice.value = storages

        return DeviceStorageScanTask.Result(itemCount = storages.size)
    }

    private suspend fun scanStorageContents(task: StorageContentScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageContents(): $task" }
        val target = task.target

        val scanner = storageScanner.get()
        val contents = scanner.scan(target)

        storageContents.value = storageContents.value.mutate {
            this[target] = contents
        }

        return DeviceStorageScanTask.Result(itemCount = contents.size)
    }

    data class Data(
        val storages: Set<DeviceStorage> = emptySet(),
        val contents: Map<DeviceStorage.Id, Collection<StorageContent>> = emptyMap(),
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