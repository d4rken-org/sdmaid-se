package eu.darken.sdmse.squeezer.core.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {

    @Query("SELECT EXISTS(SELECT 1 FROM compression_history WHERE content_hash = :contentHash)")
    suspend fun exists(contentHash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CompressionHistoryEntity)

    @Query("SELECT COUNT(*) FROM compression_history")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM compression_history")
    suspend fun clear()
}
