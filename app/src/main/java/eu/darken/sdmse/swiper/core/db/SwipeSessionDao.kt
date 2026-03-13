package eu.darken.sdmse.swiper.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeSessionDao {

    @Query("SELECT * FROM swipe_sessions WHERE state != 'COMPLETED' ORDER BY last_modified_at DESC LIMIT 1")
    fun getActiveSession(): Flow<SwipeSessionEntity?>

    @Query("SELECT * FROM swipe_sessions WHERE state != 'COMPLETED' ORDER BY last_modified_at DESC")
    fun getAllActiveSessions(): Flow<List<SwipeSessionEntity>>

    @Query("SELECT * FROM swipe_sessions WHERE session_id = :sessionId")
    suspend fun getSession(sessionId: String): SwipeSessionEntity?

    @Query("SELECT * FROM swipe_sessions WHERE session_id = :sessionId")
    fun getSessionFlow(sessionId: String): Flow<SwipeSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SwipeSessionEntity)

    @Update
    suspend fun update(session: SwipeSessionEntity)

    @Query("UPDATE swipe_sessions SET current_index = :index, last_modified_at = :lastModified WHERE session_id = :sessionId")
    suspend fun updateCurrentIndex(sessionId: String, index: Int, lastModified: Long)

    @Query("UPDATE swipe_sessions SET state = :state, last_modified_at = :lastModified WHERE session_id = :sessionId")
    suspend fun updateState(sessionId: String, state: String, lastModified: Long)

    @Query("DELETE FROM swipe_sessions WHERE session_id = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("UPDATE swipe_sessions SET label = :label WHERE session_id = :sessionId")
    suspend fun updateLabel(sessionId: String, label: String?)

    @Query("UPDATE swipe_sessions SET kept_count = kept_count + :keptDelta, deleted_count = deleted_count + :deletedDelta, last_modified_at = :lastModified WHERE session_id = :sessionId")
    suspend fun incrementProcessedCounts(sessionId: String, keptDelta: Int, deletedDelta: Int, lastModified: Long)
}
