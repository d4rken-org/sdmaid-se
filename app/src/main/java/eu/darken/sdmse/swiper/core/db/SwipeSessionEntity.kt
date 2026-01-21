package eu.darken.sdmse.swiper.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SwipeSession
import java.time.Instant
import java.util.UUID

@Entity(tableName = "swipe_sessions")
data class SwipeSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id") val sessionId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_paths") val sourcePaths: List<APath>,
    @ColumnInfo(name = "current_index") val currentIndex: Int = 0,
    @ColumnInfo(name = "total_items") val totalItems: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "last_modified_at") val lastModifiedAt: Instant = Instant.now(),
    @ColumnInfo(name = "state") val state: SessionState = SessionState.CREATED,
    @ColumnInfo(name = "label") val label: String? = null,
) {
    fun toModel() = SwipeSession(
        sessionId = sessionId,
        sourcePaths = sourcePaths,
        currentIndex = currentIndex,
        totalItems = totalItems,
        createdAt = createdAt,
        lastModifiedAt = lastModifiedAt,
        state = state,
        label = label,
    )

    companion object {
        fun from(session: SwipeSession) = SwipeSessionEntity(
            sessionId = session.sessionId,
            sourcePaths = session.sourcePaths,
            currentIndex = session.currentIndex,
            totalItems = session.totalItems,
            createdAt = session.createdAt,
            lastModifiedAt = session.lastModifiedAt,
            state = session.state,
            label = session.label,
        )
    }
}
