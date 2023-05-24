package eu.darken.sdmse.analyzer.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanner
import eu.darken.sdmse.analyzer.core.storage.StorageScanTask
import eu.darken.sdmse.analyzer.core.storage.StorageScanner
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
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
    private val storageScanner: Provider<StorageScanner>,
) : SDMTool, Progress.Client {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val storageDevices = MutableStateFlow(emptySet<DeviceStorage>())
    private val storageCategories = MutableStateFlow(emptyMap<DeviceStorage.Id, Collection<ContentCategory>>())
    val data: Flow<Data> = combine(
        storageDevices,
        storageCategories,
    ) { storages, categories ->
        val allGroups = categories
            .map { category ->
                category.value
                    .map { it.groups }
                    .flatten()
                    .map { it.id to it }

            }
            .flatten()
            .toMap()

        Data(
            storages = storages,
            categories = categories,
            groups = allGroups
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
                is StorageScanTask -> scanStorageContents(task)
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

        storageDevices.value = emptySet()
        storageCategories.value = emptyMap()

        val scanner = deviceScanner.get()
        val storages = scanner.withProgress(this) { scan() }

        storageDevices.value = storages

        return DeviceStorageScanTask.Result(itemCount = storages.size)
    }

    private suspend fun scanStorageContents(task: StorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageContents(): $task" }
        val target = storageDevices.value.single { it.id == task.target }

        val scanner = storageScanner.get()
        val categories = scanner.withProgress(this) { scan(target) }

        storageCategories.value = storageCategories.value.mutate {
            this[target.id] = categories
        }

        return DeviceStorageScanTask.Result(itemCount = 0)
    }

    data class Data(
        val storages: Set<DeviceStorage> = emptySet(),
        val categories: Map<DeviceStorage.Id, Collection<ContentCategory>> = emptyMap(),
        val groups: Map<ContentGroup.Id, ContentGroup> = emptyMap(),
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