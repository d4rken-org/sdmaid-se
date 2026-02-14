package eu.darken.sdmse.stats.core

import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceHistoryRepo @Inject constructor(
    private val reportsDatabase: ReportsDatabase,
) {
    fun getHistory(storageId: String, since: Instant): Flow<List<SpaceSnapshotEntity>> {
        return reportsDatabase.spaceSnapshotDao.getByStorageId(storageId, since)
    }

    fun getAllHistory(since: Instant): Flow<List<SpaceSnapshotEntity>> {
        return reportsDatabase.spaceSnapshotDao.getAll(since)
    }

    fun getAvailableStorageIds(): Flow<List<String>> {
        return reportsDatabase.spaceSnapshotDao.getDistinctStorageIds()
    }

    fun getReports(since: Instant): Flow<List<ReportEntity>> = reportsDatabase.getReportsSince(since)

    suspend fun insertIfNotRecent(
        storageId: String,
        recordedAt: Instant,
        spaceFree: Long,
        spaceCapacity: Long,
        dedupeWindow: Duration? = Duration.ofMinutes(5),
    ): Boolean {
        if (spaceCapacity <= 0L) return false

        reportsDatabase.withTransaction {
            if (dedupeWindow != null) {
                val latest = reportsDatabase.spaceSnapshotDao.getLatest(storageId)
                if (latest != null && !latest.recordedAt.isBefore(recordedAt.minus(dedupeWindow))) {
                    reportsDatabase.spaceSnapshotDao.deleteById(latest.id)
                }
            }

            reportsDatabase.spaceSnapshotDao.insert(
                SpaceSnapshotEntity(
                    storageId = storageId,
                    recordedAt = recordedAt,
                    spaceFree = spaceFree,
                    spaceCapacity = spaceCapacity,
                )
            )
        }
        reportsDatabase.refreshDatabaseSize()
        return true
    }
}
