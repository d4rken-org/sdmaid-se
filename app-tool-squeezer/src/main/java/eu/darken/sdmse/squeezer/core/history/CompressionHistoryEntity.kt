package eu.darken.sdmse.squeezer.core.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compression_history")
data class CompressionHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "content_hash") val contentHash: String,
    @ColumnInfo(name = "outcome") val outcome: Outcome = Outcome.COMPRESSED,
) {
    enum class Outcome {
        COMPRESSED,
        TRIED_NO_SAVINGS,
    }
}
