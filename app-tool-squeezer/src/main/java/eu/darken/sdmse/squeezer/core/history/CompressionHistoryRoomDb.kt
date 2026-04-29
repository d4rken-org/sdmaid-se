package eu.darken.sdmse.squeezer.core.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CompressionHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(CompressionHistoryConverters::class)
abstract class CompressionHistoryRoomDb : RoomDatabase() {
    abstract fun historyDao(): CompressionHistoryDao
}
