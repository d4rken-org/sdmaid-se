package eu.darken.sdmse.swiper.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.room.APathListTypeConverter
import eu.darken.sdmse.common.room.APathTypeConverter
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SwiperDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        json: Json,
    ): SwiperRoomDb = Room.databaseBuilder(context, SwiperRoomDb::class.java, "swiper.db").apply {
        addTypeConverter(APathTypeConverter(json))
        addTypeConverter(APathListTypeConverter(json))
        addTypeConverter(FileTypeFilterConverter(json))
        addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    }.build()

    @Provides
    @Singleton
    fun provideSessionDao(db: SwiperRoomDb): SwipeSessionDao = db.sessions()

    @Provides
    @Singleton
    fun provideItemDao(db: SwiperRoomDb): SwipeItemDao = db.items()

    companion object {
        internal val TAG = logTag("Swiper", "Database")

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE swipe_sessions ADD COLUMN file_type_filter TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE swipe_sessions ADD COLUMN sort_order TEXT NOT NULL DEFAULT 'OLDEST_FIRST'"
                )
            }
        }
    }
}
