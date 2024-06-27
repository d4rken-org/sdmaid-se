package eu.darken.sdmse.stats.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.common.room.InstantConverter
import eu.darken.sdmse.common.room.SDMToolTypeConverter
import eu.darken.sdmse.stats.core.db.converter.AffectedFileActionConverter
import eu.darken.sdmse.stats.core.db.converter.ReportIdTypeConverter
import eu.darken.sdmse.stats.core.db.converter.ReportStatusConverter

@Database(
    entities = [
        ReportEntity::class,
        AffectedPathEntity::class,
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
    AffectedFileActionConverter::class,
    APathTypeConverter::class,
)
abstract class ReportsRoomDb : RoomDatabase() {
    abstract fun reports(): ReportsDao
    abstract fun files(): AffectedFilesDao
}