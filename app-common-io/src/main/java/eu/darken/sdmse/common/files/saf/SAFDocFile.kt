package eu.darken.sdmse.common.files.saf

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import android.system.StructStat
import android.text.TextUtils
import eu.darken.sdmse.common.asSequence
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.useQuietly
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.util.*


data class SAFDocFile(
    private val context: Context,
    private val resolver: ContentResolver,
    val uri: Uri
) {

    val name: String?
        get() = queryForString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

    val exists: Boolean
        get() = queryForString(DocumentsContract.Document.COLUMN_DOCUMENT_ID) != null

    private val mimeType: String? by lazy { queryForString(DocumentsContract.Document.COLUMN_MIME_TYPE) }

    val isFile: Boolean
        get() = DocumentsContract.Document.MIME_TYPE_DIR != (mimeType) && mimeType?.isNotEmpty() == true

    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

    val writable: Boolean
        get() {
            // Ignore if grant doesn't allow write
            if (!hasPermission(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
                return false
            }

            val flags: Int = queryForLong(DocumentsContract.Document.COLUMN_FLAGS)?.toInt() ?: 0

            // Ignore documents without MIME
            if (TextUtils.isEmpty(mimeType)) return false

            // Deletable documents considered writable
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                return true
            }

            if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType && flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) {
                // Directories that allow create considered writable
                return true
            } else if (!TextUtils.isEmpty(mimeType) && flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0) {
                // Writable normal files considered writable
                return true
            }

            return false
        }

    val readable: Boolean
        get() {
            // Ignore if grant doesn't allow read
            if (!hasPermission(Intent.FLAG_GRANT_READ_URI_PERMISSION)) return false

            // Ignore documents without MIME
            if (TextUtils.isEmpty(mimeType)) return false

            return true
        }

    val lastModified: Instant
        get() = Instant.ofEpochMilli(queryForLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0)

    val length: Long
        get() = queryForLong(DocumentsContract.Document.COLUMN_SIZE) ?: 0

    fun createDirectory(name: String): SAFDocFile {
        return createFile(DocumentsContract.Document.MIME_TYPE_DIR, name)
    }

    fun createFile(mimeType: String, name: String): SAFDocFile {
        val newFileUri = DocumentsContract.createDocument(resolver, uri, mimeType, name)
        requireNotNull(newFileUri) { "createFile(mimeType=$mimeType, name=$name) failed for $uri" }
        return SAFDocFile(context, resolver, newFileUri)
    }

    // https://commonsware.com/blog/2019/11/23/scoped-storage-stories-documentscontract.html
    @SuppressLint("Recycle")
    fun findFile(name: String): SAFDocFile? {
        val childrenUri: Uri =
            DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

        val foundUris = resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME}=?",
            arrayOf(name),
            null
        )?.useQuietly { cursor ->
            cursor.asSequence()
                .map { Pair(it.getString(0), it.getString(1)) }
                .toList()
        }

        requireNotNull(foundUris) { "Unable to query for $name in $uri" }

        val pair = foundUris.singleOrNull { it.second == name } ?: return null

        return SAFDocFile(context, resolver, DocumentsContract.buildDocumentUriUsingTree(uri, pair.first))
    }

    @SuppressLint("Recycle") fun listFiles(): List<SAFDocFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

        val foundUris = resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        )?.useQuietly { cursor ->
            cursor.asSequence().map { DocumentsContract.buildDocumentUriUsingTree(uri, it.getString(0)) }.toList()
        }

        requireNotNull(foundUris) { "Unable to list files for $uri" }

        return foundUris.map { SAFDocFile(context, resolver, it) }
    }

    fun delete(): Boolean = try {
        if (Bugs.isDryRun) {
            log(SAFGateway.TAG, WARN) { "DRYRUN: Not deleting $uri" }
            exists
        } else {
            DocumentsContract.deleteDocument(resolver, uri)
        }
    } catch (e: IllegalArgumentException) {
        if (e.message?.contains(FileNotFoundException::class.simpleName!!) == true) false else throw e
    }

    fun setLastModified(lastModified: Instant): Boolean = try {
        val updateValues = ContentValues()
        updateValues.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified.toEpochMilli())
        val updated: Int = resolver.update(uri, updateValues, null, null)
        updated == 1
    } catch (e: Exception) {
        log(SAFGateway.TAG, WARN) {
            "setLastModified(lastModified=$lastModified) failed on $this: ${e.asLog()}"
        }
        false
    }

    fun setPermissions(permissions: Permissions): Boolean = openPFD(resolver, FileMode.WRITE).use { pfd ->
        try {
            Os.fchmod(pfd.fileDescriptor, permissions.mode)
            true
        } catch (e: Exception) {
            log(SAFGateway.TAG, WARN) { "setPermissions(permissions=$permissions) failed on $this: ${e.asLog()}" }
            false
        }
    }

    fun setOwnership(ownership: Ownership): Boolean = openPFD(resolver, FileMode.WRITE).use { pfd ->
        try {
            Os.fchown(pfd.fileDescriptor, ownership.userId.toInt(), ownership.groupId.toInt())
            true
        } catch (e: Exception) {
            log(SAFGateway.TAG, WARN) { "setOwnership(ownership=$ownership) failed on $this: ${e.asLog()}" }
            false
        }
    }

    fun fstat(): StructStat? {
        return try {
            val pfd = openPFD(resolver, FileMode.READ)
            pfd.use { Os.fstat(pfd.fileDescriptor) }
        } catch (e: Exception) {
            log(SAFGateway.TAG, WARN) { "Failed to fstat SAFPath: $this: ${e.asLog()}" }
            null
        }
    }

    internal fun openPFD(contentResolver: ContentResolver, mode: FileMode): ParcelFileDescriptor {
        return contentResolver.openFileDescriptor(uri, mode.value) ?: throw IOException("Couldn't open $uri")
    }

    private fun hasPermission(
        flag: Int
    ): Boolean = context.checkCallingOrSelfUriPermission(uri, flag) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("Recycle")
    private fun queryForString(column: String): String? {
        return try {
            resolver.query(uri, arrayOf(column), null, null, null).useQuietly { c ->
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    c.getString(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            if (Bugs.isTrace) {
                log(SAFGateway.TAG + ":SAFDocFile", WARN) { "queryForString(column=$column): ${e.asLog()}" }
            }
            null
        }
    }

    @SuppressLint("Recycle")
    private fun queryForLong(column: String): Long? {
        return try {
            resolver.query(uri, arrayOf(column), null, null, null).useQuietly { c ->
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    c.getLong(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            log(SAFGateway.TAG + ":SAFDocFile", WARN) { "queryForLong(column=$column): ${e.asLog()}" }
            null
        }
    }

    override fun toString(): String {
        return "SAFDocFile(uri=$uri)"
    }

    companion object {

        fun buildTreeUri(baseUri: Uri, crumbs: List<String>): Uri {
            val uriBuilder = StringBuilder().apply {
                append(baseUri)
                append("/document/")
                append(Uri.encode(DocumentsContract.getTreeDocumentId(baseUri)))
                if (crumbs.isNotEmpty() && !this.endsWith(Uri.encode(File.separator))) {
                    append(Uri.encode(File.separator))
                }
                crumbs.forEach {
                    if (it != crumbs.first()) append(Uri.encode(File.separator))
                    append(Uri.encode(it))
                }
            }
            return Uri.parse(uriBuilder.toString())
        }

        fun fromTreeUri(context: Context, contentResolver: ContentResolver, treeUri: Uri): SAFDocFile {
            val documentId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            return SAFDocFile(
                context,
                contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            )
        }
    }
}