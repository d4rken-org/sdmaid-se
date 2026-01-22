package eu.darken.sdmse.compressor.core.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {

    @Query("SELECT * FROM compression_history WHERE path_hash = :pathHash")
    suspend fun getByPathHash(pathHash: String): CompressionHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompressionHistoryEntity)

    @Query("SELECT COUNT(*) FROM compression_history")
    fun getCount(): Flow<Int>

    @Query("SELECT path_hash FROM compression_history")
    suspend fun getAllPathHashes(): List<String>

    @Query("DELETE FROM compression_history")
    suspend fun clear()
}
