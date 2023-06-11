package eu.darken.sdmse.analyzer.core.device

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.setup.storage.StorageSetupModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class DeviceStorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageSetupModule: StorageSetupModule,
    private val environment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val storageStatsmanager: StorageStatsManager,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun scan(): Set<DeviceStorage> {
        log(TAG) { "Scanning..." }

        updateProgressPrimary(R.string.analyzer_progress_scanning_device)

        val setupIncomplete = !storageSetupModule.state.first().isComplete

        val primaryDevice = run {
            updateProgressSecondary(R.string.analyzer_storage_type_primary_title)
            val id = StorageId(
                internalId = null,
                externalId = StorageManager.UUID_DEFAULT,
            )

            val totalBytes = try {
                storageStatsmanager.getTotalBytes(id.externalId).also {
                    if (it == 0L) throw IllegalStateException("Total bytes is 0")
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to get total bytes for $id" }
                environment.dataDir.asFile().totalSpace
            }
            val freeBytes = try {
                storageStatsmanager.getFreeBytes(id.externalId).also {
                    if (it == 0L) throw IllegalStateException("Free bytes is 0")
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to get free bytes for $id" }
                environment.dataDir.asFile().freeSpace
            }

            DeviceStorage(
                id = id,
                label = R.string.analyzer_storage_type_primary_title.toCaString(),
                type = DeviceStorage.Type.PRIMARY,
                hardware = DeviceStorage.Hardware.BUILT_IN,
                spaceCapacity = totalBytes,
                spaceFree = freeBytes,
                setupIncomplete = setupIncomplete,
            )
        }

        log(TAG) { "Primary: $primaryDevice" }
        updateProgressSecondary(R.string.analyzer_storage_type_secondary_title)

        val secondaryDevices: Set<DeviceStorage> = (storageManager2.volumes ?: emptySet())
            .filter { it.isPrimary == false && it.fsUuid != null && it.isMounted }
            .mapNotNull { volume ->
                updateProgressSecondary(
                    volume.path?.path?.toCaString() ?: R.string.analyzer_storage_type_secondary_title.toCaString()
                )

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

                val id = StorageId(internalId = volume.fsUuid, externalId = volumeId)

                val totalBytes = try {
                    // Secondary storage isn't available in on all APIs, (e.g. not on a Redmi 7A @ Android 9)
                    storageStatsmanager.getTotalBytes(id.externalId).also {
                        if (it == 0L) throw IllegalStateException("Total bytes is 0")
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to get total bytes for $id" }
                    volume.path?.totalSpace ?: 0L
                }
                val freeBytes = try {
                    storageStatsmanager.getFreeBytes(id.externalId).also {
                        // Was 0 for Xiaomi/pine_eea/pine:9/PKQ1.190319.001/V11.0.18.0.PCMEUXM:user/release-keys
                        if (it == 0L) throw IllegalStateException("Free bytes is 0")
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to get free bytes for $id" }
                    volume.path?.freeSpace ?: 0L
                }

                DeviceStorage(
                    id = id,
                    label = R.string.analyzer_storage_type_secondary_title.toCaString(),
                    type = DeviceStorage.Type.SECONDARY,
                    hardware = DeviceStorage.Hardware.SDCARD,
                    spaceCapacity = totalBytes,
                    spaceFree = freeBytes,
                    setupIncomplete = setupIncomplete,
                )
            }
            .toSet()

        log(TAG) { "Secondary devices: $secondaryDevices" }

        return setOf(primaryDevice) + secondaryDevices
    }

    companion object {
        private val TAG = logTag("Analyzer", "DeviceStorage", "Scanner")
    }
}