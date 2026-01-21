package eu.darken.sdmse.swiper.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.darken.sdmse.swiper.core.SwipeDecision
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeItemDao {

    @Query("SELECT * FROM swipe_items WHERE session_id = :sessionId ORDER BY item_index ASC")
    fun getItemsForSession(sessionId: String): Flow<List<SwipeItemEntity>>

    @Query("SELECT * FROM swipe_items WHERE session_id = :sessionId ORDER BY item_index ASC")
    suspend fun getItemsForSessionSync(sessionId: String): List<SwipeItemEntity>

    @Query("SELECT * FROM swipe_items WHERE session_id = :sessionId AND decision = :decision ORDER BY item_index ASC")
    suspend fun getItemsByDecisionSync(sessionId: String, decision: SwipeDecision): List<SwipeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SwipeItemEntity>)

    @Query("UPDATE swipe_items SET decision = :decision WHERE id = :itemId")
    suspend fun updateDecision(itemId: Long, decision: SwipeDecision)

    @Query("DELETE FROM swipe_items WHERE session_id = :sessionId")
    suspend fun deleteItemsForSession(sessionId: String)

    @Query("DELETE FROM swipe_items WHERE session_id = :sessionId AND decision IN (:decisions)")
    suspend fun deleteByDecisions(sessionId: String, decisions: List<SwipeDecision>)

    @Query("DELETE FROM swipe_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("SELECT COUNT(*) FROM swipe_items WHERE session_id = :sessionId AND decision = :decision")
    suspend fun countByDecision(sessionId: String, decision: SwipeDecision): Int

    @Query("SELECT decision, COUNT(*) as count FROM swipe_items WHERE session_id = :sessionId GROUP BY decision")
    fun getDecisionStatsFlow(sessionId: String): Flow<List<DecisionStats>>
}

data class DecisionStats(
    val decision: SwipeDecision,
    val count: Int,
)
