package eu.darken.sdmse.analyzer.core.device

import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import java.util.UUID
import javax.inject.Inject

class DeviceStorageScanner @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val storageManager2: StorageManager2,
    private val storageStatsmanager: StorageStatsManager,
) {
    suspend fun scan(): Set<DeviceStorage> {
        log(TAG) { "Scanning..." }

        val primaryDevice = run {
            val id = DeviceStorage.Id(
                internalId = null,
                externalId = StorageManager.UUID_DEFAULT,
            )

            DeviceStorage(
                id = id,
                label = R.string.analyzer_storage_type_primary_title.toCaString(),
                type = DeviceStorage.Type.PRIMARY,
                hardware = DeviceStorage.Hardware.BUILT_IN,
                spaceCapacity = storageStatsmanager.getTotalBytes(id.externalId),
                spaceFree = storageStatsmanager.getFreeBytes(id.externalId),
            )
        }

        log(TAG) { "Primary: $primaryDevice" }

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

                val id = DeviceStorage.Id(
                    internalId = volume.fsUuid,
                    externalId = volumeId,
                )

                DeviceStorage(
                    id = id,
                    label = R.string.analyzer_storage_type_secondary_title.toCaString(),
                    type = DeviceStorage.Type.SECONDARY,
                    hardware = DeviceStorage.Hardware.SDCARD,
                    spaceCapacity = storageStatsmanager.getTotalBytes(id.externalId),
                    spaceFree = storageStatsmanager.getFreeBytes(id.externalId),
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