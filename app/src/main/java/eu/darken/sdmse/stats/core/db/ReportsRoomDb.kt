package eu.darken.sdmse.stats.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.sdmse.common.room.InstantConverter

@Database(
    entities = [
        ReportEntity::class,
    ],
    version = 1,
    autoMigrations = [
        //AutoMigration(1, 2)
    ],
    exportSchema = true,

    )
@TypeConverters(
    InstantConverter::class,
    ReportIdTypeConverter::class,
    SDMToolTypeConverter::class,
    ReportStatusConverter::class,
)
abstract class ReportsRoomDb : RoomDatabase() {
    abstract fun reports(): ReportsDao
}