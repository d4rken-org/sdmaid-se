package eu.darken.sdmse.squeezer.core.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {

    @Query("SELECT * FROM compression_history WHERE content_hash = :contentHash LIMIT 1")
    suspend fun get(contentHash: String): CompressionHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompressionHistoryEntity)

    @Query("SELECT COUNT(*) FROM compression_history")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM compression_history")
    suspend fun clear()
}
