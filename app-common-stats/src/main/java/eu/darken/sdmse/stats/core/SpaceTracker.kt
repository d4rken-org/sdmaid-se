package eu.darken.sdmse.stats.core

import android.os.storage.StorageManager
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
import eu.darken.sdmse.common.storage.StorageSpaceReconcile
import eu.darken.sdmse.common.storage.StorageStatsManager2
import kotlinx.coroutines.CancellationException
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

            if (inserted > 0) {
                statsSettings.lastSnapshotAt.value(now.toEpochMilli())
            }

            log(TAG) { "recordSnapshot(force=$force): inserted=$inserted, scanned=${storages.size}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "recordSnapshot(force=$force) failed: ${e.asLog()}" }
        }
    }

    suspend fun recordSnapshot(storages: Set<StorageSnapshot>) = withContext(dispatcherProvider.IO) {
        try {
            if (storages.isEmpty()) return@withContext

            val now = Instant.now()
            if (isGloballyThrottled(now)) return@withContext

            val inserted = insertSnapshots(storages, now)
            if (inserted > 0) {
                statsSettings.lastSnapshotAt.value(now.toEpochMilli())
            }

            log(TAG) { "recordSnapshot(storages=${storages.size}): inserted=$inserted" }
        } catch (e: CancellationException) {
            throw e
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

    /**
     * Reads the current snapshot of the primary (built-in) storage volume.
     *
     * Self-contained and IO-dispatched: safe to call from any context (e.g. a home-screen widget
     * refresh). Falls back to the [java.io.File] API when [StorageStatsManager2] is unavailable, and
     * returns `null` only if even the fallback throws.
     */
    suspend fun readPrimaryStorage(): StorageSnapshot? = withContext(dispatcherProvider.IO) {
        try {
            val primaryUuid = StorageManager.UUID_DEFAULT ?: UUID.fromString("00000000-0000-0000-0000-000000000000")
            val storageId = StorageId(
                internalId = null,
                externalId = primaryUuid,
            )
            val dataDirFile = storageEnvironment.dataDir.asFile()
            val fileTotal = dataDirFile.totalSpace
            val fileFree = dataDirFile.freeSpace

            val (totalBytes, freeBytes) = try {
                // Keep the pair coupled: if either call fails the other is suspect too.
                val statsTotal = storageStatsManager.getTotalBytes(storageId)
                val statsFree = storageStatsManager.getFreeBytes(storageId)

                val reconciled = StorageSpaceReconcile.reconcilePrimary(
                    statsTotal = statsTotal,
                    statsFree = statsFree,
                    fileTotal = fileTotal,
                    fileFree = fileFree,
                )
                if (reconciled.usedFileFallback) {
                    log(TAG, WARN) {
                        "Primary StorageStats total=$statsTotal implausible vs File=$fileTotal; using File"
                    }
                }
                reconciled.total to reconciled.free
            } catch (e: Exception) {
                log(TAG, WARN) { "StorageStatsManager failed for primary storage, using File API: ${e.asLog()}" }
                fileTotal to fileFree
            }

            StorageSnapshot(
                storageId = storageId.externalId.toString(),
                spaceFree = freeBytes,
                spaceCapacity = totalBytes,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "readPrimaryStorage() failed: ${e.asLog()}" }
            null
        }
    }

    /**
     * Reads snapshots of the mounted secondary volumes (SD card, USB, adopted storage).
     *
     * Self-contained, IO-dispatched, and safe: returns an empty list if the volumes can't be
     * enumerated. Same [StorageStatsManager2] → [java.io.File] fallback as the primary read.
     */
    suspend fun readSecondaryStorages(): List<StorageSnapshot> = withContext(dispatcherProvider.IO) {
        try {
            (storageManager2.volumes ?: emptySet())
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
                    val isFatUuid = volumeUuid.toString().startsWith(StorageId.FAT_UUID_PREFIX)
                    val fileTotal = volume.path?.totalSpace ?: 0L
                    val fileFree = volume.path?.freeSpace ?: 0L

                    val (totalBytes, freeBytes) = try {
                        val statsTotal = storageStatsManager.getTotalBytes(storageId)
                        val statsFree = storageStatsManager.getFreeBytes(storageId)

                        val reconciled = StorageSpaceReconcile.reconcileSecondary(
                            statsTotal = statsTotal,
                            statsFree = statsFree,
                            fileTotal = fileTotal,
                            fileFree = fileFree,
                            isFatUuid = isFatUuid,
                        )
                        if (reconciled.usedFileFallback) {
                            log(TAG, WARN) {
                                "StorageStats total=$statsTotal disagrees with File=$fileTotal for FAT $storageId; using File"
                            }
                        }
                        reconciled.total to reconciled.free
                    } catch (e: Exception) {
                        log(TAG, WARN) { "StorageStatsManager failed for $storageId, using File API: ${e.asLog()}" }
                        fileTotal to fileFree
                    }

                    if (totalBytes <= 0L) {
                        log(TAG, WARN) { "Secondary volume reports zero capacity, skipping: $volume" }
                        return@mapNotNull null
                    }

                    StorageSnapshot(
                        storageId = storageId.externalId.toString(),
                        spaceFree = freeBytes,
                        spaceCapacity = totalBytes,
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "readSecondaryStorages() failed: ${e.asLog()}" }
            emptyList()
        }
    }

    private suspend fun readCurrentStorages(): Set<StorageSnapshot> {
        return setOfNotNull(readPrimaryStorage()) + readSecondaryStorages()
    }

    data class StorageSnapshot(
        val storageId: String,
        val spaceFree: Long,
        val spaceCapacity: Long,
    )

    companion object {
        private val GLOBAL_THROTTLE: Duration = Duration.ofMinutes(30)
        private val TAG = logTag("Stats", "SpaceTracker")
    }
}
