package eu.darken.sdmse.swiper.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.sdmse.common.room.APathListTypeConverter
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.common.room.InstantConverter

@Database(
    entities = [
        SwipeSessionEntity::class,
        SwipeItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    InstantConverter::class,
    SwipeDecisionConverter::class,
    SessionStateConverter::class,
    APathTypeConverter::class,
    APathListTypeConverter::class,
)
abstract class SwiperRoomDb : RoomDatabase() {
    abstract fun sessions(): SwipeSessionDao
    abstract fun items(): SwipeItemDao
}
