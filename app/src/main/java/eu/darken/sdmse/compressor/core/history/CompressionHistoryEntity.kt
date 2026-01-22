package eu.darken.sdmse.compressor.core.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "compression_history")
data class CompressionHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "path_hash") val pathHash: String,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "original_size") val originalSize: Long,
    @ColumnInfo(name = "compressed_size") val compressedSize: Long,
    @ColumnInfo(name = "quality") val quality: Int,
    @ColumnInfo(name = "compressed_at") val compressedAt: Instant,
)
