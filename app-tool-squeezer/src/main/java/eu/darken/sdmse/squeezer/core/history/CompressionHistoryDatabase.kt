package eu.darken.sdmse.squeezer.core.history

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.common.hashing.hash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompressionHistoryDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewaySwitch: GatewaySwitch,
    private val videoContentHasher: VideoContentHasher,
) {

    private val database by lazy {
        Room
            .databaseBuilder(context, CompressionHistoryRoomDb::class.java, DB_NAME)
            // Compression history is a derived cache — rebuilding it on schema mismatch is
            // always preferable to crashing the app on upgrade.
            .fallbackToDestructiveMigration(dropAllTables = true)
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

    suspend fun hasBeenCompressed(contentHash: String): Boolean {
        return historyDao.get(contentHash)?.outcome == CompressionHistoryEntity.Outcome.COMPRESSED
    }

    /**
     * Returns true if a previous attempt on this content already yielded no savings. The user's
     * retry escape hatch is clearing the compression history from settings.
     */
    suspend fun isKnownToNotSave(contentHash: String): Boolean {
        return historyDao.get(contentHash)?.outcome == CompressionHistoryEntity.Outcome.TRIED_NO_SAVINGS
    }

    suspend fun computeContentHash(path: APath): String {
        return gatewaySwitch.file(path, readWrite = false).source().hash(Hasher.Type.SHA256).format()
    }

    suspend fun computeVideoContentHash(path: APath): String {
        return videoContentHasher.computePartialHash(path)
    }

    suspend fun recordCompression(contentHash: String) {
        log(TAG, INFO) { "recordCompression($contentHash)" }
        historyDao.insert(
            CompressionHistoryEntity(
                contentHash = contentHash,
                outcome = CompressionHistoryEntity.Outcome.COMPRESSED,
            )
        )
        refreshDatabaseSize()
    }

    suspend fun recordNoSavings(contentHash: String) {
        log(TAG, INFO) { "recordNoSavings($contentHash)" }
        historyDao.insert(
            CompressionHistoryEntity(
                contentHash = contentHash,
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
    }
}
