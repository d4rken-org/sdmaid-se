package eu.darken.sdmse.compressor.core.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compression_history")
data class CompressionHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "content_hash") val contentHash: String,
)
