package eu.darken.sdmse.stats.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "space_snapshots",
    indices = [
        Index(value = ["storage_id", "recorded_at"]),
    ],
)
data class SpaceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "storage_id") val storageId: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Instant,
    @ColumnInfo(name = "space_free") val spaceFree: Long,
    @ColumnInfo(name = "space_capacity") val spaceCapacity: Long,
)
