package eu.darken.sdmse.common.files.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.Sink
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.time.Instant
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SAFGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : APathGateway<SAFPath, SAFPathLookup, SAFPathLookupExtended> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> runIO(
        block: suspend CoroutineScope.() -> T
    ): T = withContext(dispatcherProvider.IO) { block() }

    /**
     * SAFPaths have a normalized treeUri, e.g.:
     * content://com.android.externalstorage.documents/tree/primary
     * SAFDocFiles need require a treeUri that actually gives us access though, i.e. the closet SAF permission we have.
     */
    private fun findDocFile(file: SAFPath): SAFDocFile {
        val match = file.findPermission(contentResolver.persistedUriPermissions)

        if (match == null) {
            log(TAG, VERBOSE) { "No UriPermission match for $file" }
            throw MissingUriPermissionException(file)
        }

        val targetTreeUri = SAFDocFile.buildTreeUri(
            match.permission.uri,
            match.missingSegments,
        )
        return SAFDocFile.fromTreeUri(context, contentResolver, targetTreeUri)
    }

    override suspend fun createFile(path: SAFPath): Unit = runIO {
        val docFile = findDocFile(path)
        log(TAG, VERBOSE) { "createFile(): $path -> $docFile" }
        if (docFile.exists) throw WriteException(path, message = "File already exists")

        try {
            createDocumentFile(FILE_TYPE_DEFAULT, path)
        } catch (e: Exception) {
            log(TAG, WARN) { "createFile($path) failed: ${e.asLog()}" }
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun createDir(path: SAFPath): Unit = runIO {
        val docFile = findDocFile(path)
        log(TAG, VERBOSE) { "createDir(): $path -> $docFile" }
        if (docFile.exists) throw WriteException(path, message = "Directory already exists")

        try {
            createDocumentFile(DIR_TYPE, path)
        } catch (e: Exception) {
            log(TAG, WARN) { "createDir($path) failed: ${e.asLog()}" }
            throw WriteException(path, cause = e)
        }
    }

    private fun createDocumentFile(mimeType: String, targetSafPath: SAFPath): SAFDocFile {
        if (targetSafPath.segments.isEmpty()) {
            throw IllegalArgumentException("Can't create file/dir on treeRoot without segments!")
        }
        val targetName = targetSafPath.segments.last()

        val targetParentDocFile: SAFDocFile = targetSafPath.segments
            .mapIndexed { index, segment ->
                val segmentSafPath = targetSafPath.copy(
                    segments = targetSafPath.segments.drop(targetSafPath.segments.size - index)
                )
                val segmentDocFile = findDocFile(segmentSafPath)
                if (!segmentDocFile.exists) {
                    log(TAG) { "Create parent folder $segmentSafPath" }
                    segmentDocFile.createDirectory(segment)
                }

                segmentDocFile
            }
            .last()

        val existing = targetParentDocFile.findFile(targetName)

        check(existing == null) { "File already exists: ${existing?.uri}" }

        val targetDocFile = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            targetParentDocFile.createDirectory(targetName)
        } else {
            targetParentDocFile.createFile(mimeType, targetName)
        }
        require(targetName == targetDocFile.name) {
            "Unexpected name change: Wanted $targetName, but got ${targetDocFile.name}"
        }

        log(TAG) { "createDocumentFile(mimeType=$mimeType, targetSafPath=$targetSafPath" }
        return targetDocFile
    }

    override suspend fun listFiles(path: SAFPath): List<SAFPath> = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "listFiles(): $path -> $docFile" }
            docFile.listFiles().map {
                val name = it.name ?: it.uri.pathSegments.last().split('/').last()
                path.child(name)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "listFiles($path) failed." }
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun exists(path: SAFPath): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "exists(): $path -> $docFile" }
            docFile.exists
        } catch (e: Exception) {
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun delete(path: SAFPath) = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "delete(): $path -> $docFile" }

            var success = docFile.delete()

            if (!success) {
                success = !docFile.exists
                if (success) log(TAG, WARN) { "Tried to delete file, but it's already gone: $path" }
            }

            if (!success) throw IOException("Document delete() call returned false")
        } catch (e: Exception) {
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun canWrite(path: SAFPath): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "canWrite(): $path -> $docFile" }
            docFile.writable
        } catch (e: MissingUriPermissionException) {
            false
        } catch (e: Exception) {
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun canRead(path: SAFPath): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "canRead(): $path -> $docFile" }
            docFile.readable
        } catch (e: MissingUriPermissionException) {
            false
        } catch (e: Exception) {
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun lookup(path: SAFPath): SAFPathLookup = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "lookup($path) -> $docFile" }

            if (!docFile.readable) throw IOException("readable=false")

            SAFPathLookup(
                lookedUp = path,
                docFile = docFile,
            ).also {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Looked up: $it" }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "lookup($path) failed." }
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun lookupFiles(path: SAFPath): List<SAFPathLookup> = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "lookupFiles($path) -> $docFile" }

            docFile.listFiles()
                .map {
                    val name = it.name ?: it.uri.pathSegments.last().split('/').last()
                    path.child(name)
                }
                .map { lookup(it) }
                .also {
                    if (Bugs.isTrace) {
                        log(TAG, VERBOSE) { "Looked up ${it.size} items:" }
                        it.forEachIndexed { index, look -> log(TAG, VERBOSE) { "#$index $look" } }
                    }
                }
        } catch (e: Exception) {
            log(TAG, WARN) { "lookupFiles($path) failed." }
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun lookupFilesExtended(path: SAFPath): List<SAFPathLookupExtended> = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "lookupFilesExtended($path) -> $docFile" }

            docFile.listFiles()
                .map {
                    val name = it.name ?: it.uri.pathSegments.last().split('/').last()
                    path.child(name)
                }
                .map { lookup(it) }
                .map { SAFPathLookupExtended(it) }
                .also {
                    if (Bugs.isTrace) {
                        log(TAG, VERBOSE) { "Looked up ${it.size} items:" }
                        it.forEachIndexed { index, look -> log(TAG, VERBOSE) { "#$index $look" } }
                    }
                }
        } catch (e: Exception) {
            log(TAG, WARN) { "lookupFilesExtended($path) failed." }
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun walk(
        path: SAFPath,
        onFilter: (suspend (SAFPathLookup) -> Boolean)?,
        onError: (suspend (SAFPathLookup, Exception) -> Boolean)?,
    ): Flow<SAFPathLookup> = flow {
        val start = lookup(path)
        log(TAG, VERBOSE) { "walk($path) -> $start" }

        if (start.isFile) {
            emit(start)
            return@flow
        }

        val queue = LinkedList(listOf(start))

        while (!queue.isEmpty()) {
            val lookUp = queue.removeFirst()

            val newBatch = try {
                lookupFiles(lookUp.lookedUp)
            } catch (e: IOException) {
                log(TAG, ERROR) { "Failed to read $lookUp: $e" }
                if (onError != null && onError(lookUp, e)) {
                    emptyList()
                } else {
                    throw e
                }
            }

            newBatch
                .filter {
                    val allowed = onFilter == null || onFilter(it)
                    if (Bugs.isTrace) {
                        if (!allowed) log(TAG, VERBOSE) { "Skipping (filter): $it" }
                    }
                    allowed
                }
                .forEach { child ->
                    if (child.isDirectory) {
                        if (Bugs.isTrace) log(TAG, VERBOSE) { "Walking: $child" }
                        queue.addFirst(child)
                    }
                    emit(child)
                }
        }
    }
        .flowOn(dispatcherProvider.IO)
        .catch { e ->
            log(TAG, WARN) { "walk($path) failed." }
            throw ReadException(path, cause = e)
        }


    override suspend fun read(path: SAFPath): Source = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "read(): $path -> $docFile" }

            if (!docFile.readable) throw IOException("readable=false")

            val pfd = docFile.openPFD(contentResolver, FileMode.READ)
            ParcelFileDescriptor.AutoCloseInputStream(pfd).source().buffer()
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to read from $path: ${e.asLog()}" }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun write(path: SAFPath): Sink = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "write(): $path -> $docFile" }

            if (!docFile.writable) throw IOException("writable=false")

            val pfd = docFile.openPFD(contentResolver, FileMode.WRITE)
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).sink().buffer()
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to write to $path: ${e.asLog()}" }
            throw WriteException(path = path, cause = e)
        }
    }

    override suspend fun setModifiedAt(path: SAFPath, modifiedAt: Instant): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "setModifiedAt(): $path -> $docFile" }
            docFile.setLastModified(modifiedAt)
        } catch (e: Exception) {
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun setPermissions(path: SAFPath, permissions: Permissions): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "setPermissions(): $path -> $docFile" }
            docFile.setPermissions(permissions)
        } catch (e: Exception) {
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun setOwnership(path: SAFPath, ownership: Ownership): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "setOwnership(): $path -> $docFile" }
            docFile.setOwnership(ownership)
        } catch (e: Exception) {
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun createSymlink(linkPath: SAFPath, targetPath: SAFPath): Boolean {
        throw UnsupportedOperationException("SAF doesn't support symlinks. createSymlink(linkPath=$linkPath, targetPath=$targetPath)")
    }

    companion object {
        val TAG = logTag("Gateway", "SAF")

        const val RW_FLAGSINT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val DIR_TYPE: String = DocumentsContract.Document.MIME_TYPE_DIR
        private const val FILE_TYPE_DEFAULT: String = "application/octet-stream"
    }
}