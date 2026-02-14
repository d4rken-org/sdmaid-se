package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SpaceSnapshotDao {
    @Insert
    suspend fun insert(entity: SpaceSnapshotEntity)

    @Query(
        "SELECT * FROM space_snapshots " +
            "WHERE storage_id = :storageId AND recorded_at >= :since " +
            "ORDER BY recorded_at ASC"
    )
    fun getByStorageId(storageId: String, since: Instant): Flow<List<SpaceSnapshotEntity>>

    @Query("SELECT * FROM space_snapshots WHERE recorded_at >= :since ORDER BY recorded_at ASC")
    fun getAll(since: Instant): Flow<List<SpaceSnapshotEntity>>

    @Query("SELECT MAX(recorded_at) FROM space_snapshots WHERE storage_id = :storageId")
    suspend fun getLatestTimestamp(storageId: String): Instant?

    @Query("SELECT * FROM space_snapshots WHERE storage_id = :storageId ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatest(storageId: String): SpaceSnapshotEntity?

    @Query("DELETE FROM space_snapshots WHERE id = :snapshotId")
    suspend fun deleteById(snapshotId: Long)

    @Query("SELECT DISTINCT storage_id FROM space_snapshots")
    fun getDistinctStorageIds(): Flow<List<String>>

    @Query("DELETE FROM space_snapshots WHERE recorded_at < :cutOff")
    suspend fun deleteOlderThan(cutOff: Instant)

    @Query("DELETE FROM space_snapshots")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM space_snapshots")
    fun snapshotCount(): Flow<Int>
}
