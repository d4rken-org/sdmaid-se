package eu.darken.sdmse.squeezer.core.history

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.squeezer.core.ContentId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompressionHistoryDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val database by lazy {
        Room
            .databaseBuilder(context, CompressionHistoryRoomDb::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    private val historyDao: CompressionHistoryDao
        get() = database.historyDao()

    private val dbFile: File
        get() = context.getDatabasePath(DB_NAME)

    private fun getDatabaseSize(): Long {
        return listOf("", "-shm", "-wal").sumOf { suffix ->
            File(dbFile.parent, "$DB_NAME$suffix").takeIf { it.exists() }?.length() ?: 0L
        }
    }

    private val _databaseSize = MutableStateFlow(0L)
    val databaseSize: Flow<Long> = _databaseSize.onSubscription { refreshDatabaseSize() }

    private fun refreshDatabaseSize() {
        _databaseSize.value = getDatabaseSize()
    }

    val count: Flow<Int>
        get() = historyDao.getCount()

    suspend fun getOutcome(contentId: ContentId): CompressionHistoryEntity.Outcome? {
        return historyDao.get(contentId.value)?.outcome
    }

    suspend fun recordCompression(contentId: ContentId) {
        log(TAG, INFO) { "recordCompression($contentId)" }
        historyDao.insert(
            CompressionHistoryEntity(
                contentHash = contentId.value,
                outcome = CompressionHistoryEntity.Outcome.COMPRESSED,
            )
        )
        refreshDatabaseSize()
    }

    suspend fun recordNoSavings(contentId: ContentId) {
        log(TAG, INFO) { "recordNoSavings($contentId)" }
        historyDao.insert(
            CompressionHistoryEntity(
                contentHash = contentId.value,
                outcome = CompressionHistoryEntity.Outcome.TRIED_NO_SAVINGS,
            )
        )
        refreshDatabaseSize()
    }

    suspend fun clear() {
        log(TAG, INFO) { "clear()" }
        historyDao.clear()
        refreshDatabaseSize()
    }

    companion object {
        private const val DB_NAME = "compression_history"
        internal val TAG = logTag("Squeezer", "History", "Database")

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE compression_history ADD COLUMN outcome TEXT NOT NULL DEFAULT 'COMPRESSED'"
                )
            }
        }
    }
}
