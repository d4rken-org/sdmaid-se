package eu.darken.sdmse.compressor.core.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CompressionHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CompressionHistoryRoomDb : RoomDatabase() {
    abstract fun historyDao(): CompressionHistoryDao
}
