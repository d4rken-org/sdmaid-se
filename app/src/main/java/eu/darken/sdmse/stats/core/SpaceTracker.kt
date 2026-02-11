package eu.darken.sdmse.stats.core

import android.os.storage.StorageManager
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageStatsManager2
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceTracker @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val storageStatsManager: StorageStatsManager2,
    private val storageManager2: StorageManager2,
    private val storageEnvironment: StorageEnvironment,
    private val spaceHistoryRepo: SpaceHistoryRepo,
    private val statsSettings: StatsSettings,
) {

    suspend fun recordSnapshot(force: Boolean = false) = withContext(dispatcherProvider.IO) {
        try {
            val now = Instant.now()
            if (!force && isGloballyThrottled(now)) return@withContext

            val storages = readCurrentStorages()
            val inserted = insertSnapshots(storages, now)

            if (inserted > 0 || force) {
                statsSettings.lastSnapshotAt.value(now.toEpochMilli())
            }

            log(TAG) { "recordSnapshot(force=$force): inserted=$inserted, scanned=${storages.size}" }
        } catch (e: Exception) {
            log(TAG, WARN) { "recordSnapshot(force=$force) failed: ${e.asLog()}" }
        }
    }

    suspend fun recordSnapshot(storages: Set<DeviceStorage>) = withContext(dispatcherProvider.IO) {
        try {
            if (storages.isEmpty()) return@withContext

            val now = Instant.now()
            val snapshots = storages.map {
                StorageSnapshot(
                    storageId = it.id.externalId.toString(),
                    spaceFree = it.spaceFree,
                    spaceCapacity = it.spaceCapacity,
                )
            }.toSet()

            val inserted = insertSnapshots(snapshots, now)
            if (inserted > 0) {
                statsSettings.lastSnapshotAt.value(now.toEpochMilli())
            }

            log(TAG) { "recordSnapshot(storages=${storages.size}): inserted=$inserted" }
        } catch (e: Exception) {
            log(TAG, WARN) { "recordSnapshot(storages) failed: ${e.asLog()}" }
        }
    }

    private suspend fun isGloballyThrottled(now: Instant): Boolean {
        val lastAt = statsSettings.lastSnapshotAt.value()
        if (lastAt <= 0L) return false

        val last = Instant.ofEpochMilli(lastAt)
        val blocked = !last.isBefore(now.minus(GLOBAL_THROTTLE))
        if (blocked) log(TAG) { "Global snapshot throttle active (last=$last)" }
        return blocked
    }

    private suspend fun insertSnapshots(storages: Set<StorageSnapshot>, now: Instant): Int {
        var inserted = 0

        storages.forEach { storage ->
            val wasInserted = spaceHistoryRepo.insertIfNotRecent(
                storageId = storage.storageId,
                recordedAt = now,
                spaceFree = storage.spaceFree,
                spaceCapacity = storage.spaceCapacity,
            )
            if (wasInserted) {
                inserted++
            } else {
                log(TAG) { "Skipping snapshot for ${storage.storageId}" }
            }
        }

        return inserted
    }

    private suspend fun readCurrentStorages(): Set<StorageSnapshot> {
        val primary = run {
            val primaryUuid = StorageManager.UUID_DEFAULT ?: UUID.fromString("00000000-0000-0000-0000-000000000000")
            val storageId = StorageId(
                internalId = null,
                externalId = primaryUuid,
            )
            val totalBytes = try {
                storageStatsManager.getTotalBytes(storageId)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to get total bytes for primary storage: ${e.asLog()}" }
                storageEnvironment.dataDir.asFile().totalSpace
            }
            val freeBytes = try {
                storageStatsManager.getFreeBytes(storageId)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to get free bytes for primary storage: ${e.asLog()}" }
                storageEnvironment.dataDir.asFile().freeSpace
            }

            StorageSnapshot(
                storageId = storageId.externalId.toString(),
                spaceFree = freeBytes,
                spaceCapacity = totalBytes,
            )
        }

        val secondary = (storageManager2.volumes ?: emptySet())
            .filter { it.isPrimary == false && it.fsUuid != null && it.isMounted }
            .mapNotNull { volume ->
                val volumeUuid = StorageId.parseVolumeUuid(volume.fsUuid)
                if (volumeUuid == null) {
                    log(TAG, WARN) { "Failed to determine UUID for volume: $volume" }
                    return@mapNotNull null
                }

                val storageId = StorageId(
                    internalId = volume.fsUuid,
                    externalId = volumeUuid,
                )

                val totalBytes = try {
                    storageStatsManager.getTotalBytes(storageId)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to get total bytes for $storageId: ${e.asLog()}" }
                    volume.path?.totalSpace ?: 0L
                }
                val freeBytes = try {
                    storageStatsManager.getFreeBytes(storageId)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to get free bytes for $storageId: ${e.asLog()}" }
                    volume.path?.freeSpace ?: 0L
                }

                StorageSnapshot(
                    storageId = storageId.externalId.toString(),
                    spaceFree = freeBytes,
                    spaceCapacity = totalBytes,
                )
            }
            .toSet()

        return setOf(primary) + secondary
    }

    private data class StorageSnapshot(
        val storageId: String,
        val spaceFree: Long,
        val spaceCapacity: Long,
    )

    companion object {
        private val GLOBAL_THROTTLE: Duration = Duration.ofMinutes(30)
        private val TAG = logTag("Stats", "SpaceTracker")
    }
}
