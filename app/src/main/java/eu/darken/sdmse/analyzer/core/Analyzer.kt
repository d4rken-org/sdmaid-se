package eu.darken.sdmse.analyzer.core

import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.storage.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.DeviceStorageScanTask
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Analyzer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val storageEnvironment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val storageStatsmanager: StorageStatsManager,
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


        val primaryDevice = run {
            val internalId = StorageManager.UUID_DEFAULT

            DeviceStorage(
                id = internalId.toString(),
                label = R.string.analyzer_storage_type_primary_title.toCaString(),
                description = R.string.analyzer_storage_type_primary_description.toCaString(),
                hardwareType = DeviceStorage.HardwareType.BUILT_IN,
                spaceCapacity = storageStatsmanager.getTotalBytes(internalId),
                spaceFree = storageStatsmanager.getFreeBytes(internalId),
            )
        }

        val volumes = storageManager2.volumes ?: emptySet()
        val secondaryDevices: Set<DeviceStorage> = volumes
            .filter { it.isPrimary == false && it.fsUuid != null && it.isMounted }
            .mapNotNull { volume ->
                var volumeId: UUID? = try {
                    UUID.fromString(volume.fsUuid)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (volumeId == null && volume.fsUuid != null) {
                    try {
                        // StorageManager.FAT_UUID_PREFIX
                        volumeId = UUID.fromString(
                            "fafafafa-fafa-5afa-8afa-fafa" + volume.fsUuid!!.replace("-", "")
                        )
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to construct UUID: ${e.asLog()}" }
                    }
                }

                if (volumeId == null) {
                    log(TAG, WARN) { "Failed to determine UUID of $volume" }
                    return@mapNotNull null
                }

                DeviceStorage(
                    id = volumeId.toString(),
                    label = R.string.analyzer_storage_type_secondary_title.toCaString(),
                    description = R.string.analyzer_storage_type_secondary_description.toCaString(),
                    hardwareType = DeviceStorage.HardwareType.SDCARD,
                    spaceCapacity = storageStatsmanager.getTotalBytes(volumeId),
                    spaceFree = storageStatsmanager.getFreeBytes(volumeId),
                )
            }
            .toSet()

        deviceStorageData.value = setOf(primaryDevice) + secondaryDevices

        return DeviceStorageScanTask.Result(
            itemCount = 0
        )
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