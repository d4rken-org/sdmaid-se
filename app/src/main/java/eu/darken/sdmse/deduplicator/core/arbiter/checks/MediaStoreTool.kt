package eu.darken.sdmse.deduplicator.core.arbiter.checks

import android.content.ContentResolver
import android.provider.MediaStore
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import javax.inject.Inject


class MediaStoreTool @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    private val indexedMediaPaths by lazy {
        log(TAG) { "Getting indexed media files..." }
        val mediaPaths = mutableListOf<String>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                mediaPaths.add(cursor.getString(columnIndex))
            }
        } ?: log(TAG, WARN) { "Cursor was null when querying indexed media." }

        log(TAG) { "There are ${mediaPaths.size} indexed media files" }
        if (Bugs.isTrace) mediaPaths.forEachIndexed { index, path -> log(TAG, VERBOSE) { "#$index - $path" } }

        mediaPaths
    }

    private val indexedPaths by lazy {
        log(TAG) { "Getting indexed files..." }
        val filePaths = mutableListOf<String>()

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (cursor.moveToNext()) {
                filePaths.add(cursor.getString(dataIndex))
            }
        } ?: log(TAG, WARN) { "Cursor was null when querying indexed files." }

        log(TAG) { "There are ${filePaths.size} indexed files" }
        if (Bugs.isTrace) filePaths.forEachIndexed { index, path -> log(TAG, VERBOSE) { "#$index - $path" } }

        filePaths
    }

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

    companion object {
        private val TAG = logTag("Deduplicator", "Arbiter", "MediaStoreTool")
    }
}