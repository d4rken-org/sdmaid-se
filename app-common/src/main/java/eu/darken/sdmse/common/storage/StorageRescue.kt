package eu.darken.sdmse.common.storage

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-allocates a small file (RESCUE_SIZE) in the app's no-backup data dir. When the
 * device runs out of space, [releaseIfNeeded] frees that file early in app startup so
 * DataStore / Room / log writes during App init have somewhere to land. After the user
 * actually frees disk space, [restoreIfPossible] re-allocates the rescue.
 *
 * See issue #2401 for the original crash this guards against.
 */
@Singleton
class StorageRescue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val baseDir: File
        get() = context.noBackupFilesDir

    private val rescueFile: File
        get() = File(baseDir, RESCUE_FILE_NAME)

    private val mutex = Mutex()

    suspend fun restoreIfPossible() = withContext(dispatcherProvider.IO + NonCancellable) {
        mutex.withLock {
            try {
                if (rescueFile.exists() && rescueFile.length() == RESCUE_SIZE) {
                    log(TAG) { "restoreIfPossible(): already allocated" }
                    return@withLock
                }
                if (rescueFile.exists()) {
                    log(TAG) { "restoreIfPossible(): cleaning wrong-size leftover" }
                    rescueFile.delete()
                }

                val usable = baseDir.usableSpace
                if (usable < CREATE_THRESHOLD) {
                    log(TAG) { "restoreIfPossible(): not enough headroom ($usable), skipping" }
                    return@withLock
                }

                // Write actual bytes. RandomAccessFile.setLength() can produce sparse
                // files on ext4/F2FS where blocks aren't reserved, defeating the rescue.
                val buf = ByteArray(WRITE_BUFFER_SIZE)
                FileOutputStream(rescueFile).use { out ->
                    var written = 0L
                    while (written < RESCUE_SIZE) {
                        val n = minOf(buf.size.toLong(), RESCUE_SIZE - written).toInt()
                        out.write(buf, 0, n)
                        written += n
                    }
                    out.fd.sync()
                }
                log(TAG, INFO) { "Allocated rescue file: ${rescueFile.length()} bytes" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to allocate rescue file: ${e.asLog()}" }
                try {
                    rescueFile.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val STATIC_TAG = "StorageRescue"
        private val TAG = logTag(STATIC_TAG)
        private const val RESCUE_FILE_NAME = "storage_rescue.bin"
        private const val RESCUE_SIZE = 2L * 1024 * 1024
        // Release the rescue only when free space is below the rescue's own size:
        // releasing it gives us back exactly RESCUE_SIZE bytes of headroom.
        private const val RELEASE_THRESHOLD = RESCUE_SIZE
        private const val CREATE_THRESHOLD = 10L * 1024 * 1024
        private const val WRITE_BUFFER_SIZE = 64 * 1024

        /**
         * Synchronous, dependency-free, callable from [android.app.Application.attachBaseContext]
         * BEFORE Hilt field-injection runs in `super.onCreate()`. Must not throw under any
         * condition — this is the very first thing that runs on app launch.
         *
         * Hilt-injected singletons can fire DataStore/Room writes from their `init {}` blocks
         * the moment they're constructed (e.g. RecorderModule.init reads
         * debugSettings.recorderPath; ReportsDatabase.init runs Room queries). Doing the
         * release inside onCreate() AFTER super.onCreate() is too late.
         *
         * Uses [android.util.Log] directly because the project's Logging system is
         * installed inside onCreate() and isn't yet configured at attachBaseContext.
         */
        fun releaseIfNeeded(context: Context) {
            try {
                val baseDir = context.noBackupFilesDir
                val rescueFile = File(baseDir, RESCUE_FILE_NAME)
                val usable = baseDir.usableSpace

                if (usable < RELEASE_THRESHOLD && rescueFile.exists()) {
                    val deleted = rescueFile.delete()
                    val after = baseDir.usableSpace
                    Log.w(
                        STATIC_TAG,
                        "Released rescue (deleted=$deleted): usable $usable -> $after"
                    )
                }
            } catch (e: Throwable) {
                Log.e(STATIC_TAG, "releaseIfNeeded failed", e)
            }
        }
    }
}
