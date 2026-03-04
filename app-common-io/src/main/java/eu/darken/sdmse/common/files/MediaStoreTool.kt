package eu.darken.sdmse.common.files

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.storage.StorageEnvironment
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MediaStoreTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val storageEnvironment: StorageEnvironment,
) {

    suspend fun isIndexed(path: APath): Boolean {
        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.DATA),
            MediaStore.MediaColumns.DATA + "=?",
            arrayOf(path.path),
            null
        )

        if (cursor == null) {
            log(TAG, WARN) { "Cursor was null when querying media store: $path" }
            return false
        }

        val indexed = cursor.use {
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index == -1) {
                    log(TAG, WARN) { "DATA index was -1" }
                    false
                } else {
                    cursor.getString(index) == path.path
                }
            } else {
                false
            }
        }

        if (Bugs.isTrace) log(TAG, VERBOSE) { "isIndexed=$indexed $path" }

        return indexed
    }

    private val pendingPaths = mutableListOf<String>()
    private val lock = Mutex()
    private val mimeTypeMap = MimeTypeMap.getSingleton()

    suspend fun notifyDeleted(path: LocalPath) {
        if (Bugs.isDryRun) return
        if (!isMediaFile(path) || !isOnExternalStorage(path)) return
        lock.withLock { pendingPaths.add(path.path) }
    }

    suspend fun flush() {
        val paths = lock.withLock {
            pendingPaths.toList().also { pendingPaths.clear() }
        }
        if (paths.isEmpty()) return
        log(TAG) { "Notifying MediaStore about ${paths.size} deleted files" }
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun isMediaFile(path: LocalPath): Boolean {
        val ext = path.name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return false
        val mimeType = mimeTypeMap.getMimeTypeFromExtension(ext) ?: return false
        return mimeType.startsWith("image/") ||
            mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/")
    }

    private fun isOnExternalStorage(path: LocalPath): Boolean {
        val pathStr = path.path
        if (pathStr.contains("/Android/data/") || pathStr.contains("/Android/obb/")) return false
        return storageEnvironment.externalDirs.any { pathStr.startsWith(it.path) }
    }

    companion object {
        private val TAG = logTag("Arbiter", "MediaStoreTool")
    }
}
