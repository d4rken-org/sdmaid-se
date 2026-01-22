package eu.darken.sdmse.compressor.core.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.sdmse.common.room.InstantConverter

@Database(
    entities = [CompressionHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    InstantConverter::class,
)
abstract class CompressionHistoryRoomDb : RoomDatabase() {
    abstract fun historyDao(): CompressionHistoryDao
}
