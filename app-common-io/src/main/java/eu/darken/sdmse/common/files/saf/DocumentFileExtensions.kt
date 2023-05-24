package eu.darken.sdmse.common.files.saf

import android.content.ContentResolver
import android.content.ContentValues
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import android.system.StructStat
import androidx.documentfile.provider.DocumentFile
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import java.io.IOException
import java.util.Date


internal enum class FileMode constructor(val value: String) {
    WRITE("w"), READ("r")
}

internal fun DocumentFile.fstat(contentResolver: ContentResolver): StructStat? {
    return try {
        val pfd = openParcelFileDescriptor(contentResolver, FileMode.READ)
        pfd.use { Os.fstat(pfd.fileDescriptor) }
    } catch (e: Exception) {
        log(SAFGateway.TAG, WARN) { "Failed to fstat SAFPath: $this: ${e.asLog()}" }
        null
    }
}

internal fun DocumentFile.openParcelFileDescriptor(
    contentResolver: ContentResolver,
    mode: FileMode
): ParcelFileDescriptor {
    val pfd = contentResolver.openFileDescriptor(this.uri, mode.value)
    if (pfd == null) throw IOException("Couldn't open $uri")
    return pfd
}

internal fun DocumentFile.setLastModified(contentResolver: ContentResolver, lastModified: Date): Boolean =
    try {
        val updateValues = ContentValues()
        updateValues.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified.time)
        val updated: Int = contentResolver.update(uri, updateValues, null, null)
        updated == 1
    } catch (e: Exception) {
        log(SAFGateway.TAG, WARN) { "setLastModified(lastModified=$lastModified) failed on $this: ${e.asLog()}" }
        false
    }

internal fun DocumentFile.setPermissions(contentResolver: ContentResolver, permissions: Permissions): Boolean =
    openParcelFileDescriptor(contentResolver, FileMode.WRITE).use { pfd ->
        try {
            Os.fchmod(pfd.fileDescriptor, permissions.mode)
            true
        } catch (e: Exception) {
            log(SAFGateway.TAG, WARN) { "setPermissions(permissions=${permissions}) failed on $this: ${e.asLog()}" }
            false
        }
    }

internal fun DocumentFile.setOwnership(contentResolver: ContentResolver, ownership: Ownership): Boolean =
    openParcelFileDescriptor(contentResolver, FileMode.WRITE).use { pfd ->
        try {
            Os.fchown(pfd.fileDescriptor, ownership.userId.toInt(), ownership.groupId.toInt())
            true
        } catch (e: Exception) {
            log(SAFGateway.TAG, WARN) { "setOwnership(ownership=$ownership) failed on $this: ${e.asLog()}" }
            false
        }
    }