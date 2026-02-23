package eu.darken.sdmse.swiper.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.swiper.core.SwipeDecision

@Entity(
    tableName = "swipe_items",
    foreignKeys = [
        ForeignKey(
            entity = SwipeSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"])],
)
data class SwipeItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "item_index") val itemIndex: Int,
    @ColumnInfo(name = "path") val path: APath,
    @ColumnInfo(name = "decision") val decision: SwipeDecision = SwipeDecision.UNDECIDED,
)
