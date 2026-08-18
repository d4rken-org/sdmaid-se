package eu.darken.sdmse.common.files.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.FileHandle
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
            throw MissingUriPermissionException(path = file)
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
        if (docFile.exists) throw WriteException("File already exists", path)

        try {
            createDocumentFile(FILE_TYPE_DEFAULT, path)
        } catch (e: Exception) {
            log(TAG, WARN) { "createFile($path) failed: ${e.asLog()}" }
            throw WriteException(path = path, cause = e)
        }
    }

    override suspend fun createDir(path: SAFPath): Unit = runIO {
        val docFile = findDocFile(path)
        log(TAG, VERBOSE) { "createDir(): $path -> $docFile" }
        if (docFile.exists) throw WriteException("Directory already exists", path)

        try {
            createDocumentFile(DIR_TYPE, path)
        } catch (e: Exception) {
            log(TAG, WARN) { "createDir($path) failed: ${e.asLog()}" }
            throw WriteException(path = path, cause = e)
        }
    }

    private fun createDocumentFile(mimeType: String, targetSafPath: SAFPath): SAFDocFile {
        if (targetSafPath.segments.isEmpty()) {
            throw IllegalArgumentException("Can't create file/dir on treeRoot without segments!")
        }
        val targetName = targetSafPath.segments.last()

        val match = targetSafPath.findPermission(contentResolver.persistedUriPermissions)
        if (match == null) {
            log(TAG, VERBOSE) { "No UriPermission match for $targetSafPath" }
            throw MissingUriPermissionException(path = targetSafPath)
        }
        if (match.missingSegments.isEmpty()) {
            throw WriteException("Can't create the granted tree root itself", targetSafPath)
        }

        // The granted document is the deepest ancestor we can address directly, anything below it
        // may still be missing. Walking down from the volume root instead would fail for any grant
        // that is narrower than the volume, e.g. one on Android/data.
        var targetParentDocFile = SAFDocFile.fromTreeUri(context, contentResolver, match.permission.uri)

        match.missingSegments.dropLast(1).forEach { segment ->
            val existing = targetParentDocFile.findFile(segment)
            targetParentDocFile = when {
                existing == null -> {
                    log(TAG) { "Creating missing parent folder $segment for $targetSafPath" }
                    val created = targetParentDocFile.createDirectory(segment)
                    val createdName = created.name
                    if (createdName != segment) {
                        // Providers may sanitize or uniquify a name (ExternalStorageProvider does both),
                        // which would put the target below a folder we didn't ask for.
                        log(TAG, WARN) { "Parent folder $segment was created as $createdName, removing it again" }
                        try {
                            created.delete()
                        } catch (e: Exception) {
                            log(TAG, WARN) { "Failed to remove renamed parent folder $created: ${e.asLog()}" }
                        }
                        throw WriteException(
                            "Unexpected name change: Wanted parent folder $segment, but got $createdName",
                            targetSafPath,
                        )
                    }
                    created
                }

                existing.isDirectory -> existing
                else -> throw WriteException("Parent folder $segment is not a directory", targetSafPath)
            }
        }

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

    override suspend fun listFiles(path: SAFPath): Flow<SAFPath> = runIO {
        val children = childPaths(path, "listFiles")
        flow {
            children.forEach { emit(it) }
        }
            .refineErrors("listFiles", path)
            .flowOn(dispatcherProvider.IO)
    }

    override suspend fun exists(path: SAFPath): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "exists(): $path -> $docFile" }
            docFile.exists
        } catch (e: Exception) {
            throw ReadException(path = path, cause = e)
        }
    }

    suspend fun delete(path: SAFPath) = delete(path, recursive = false)

    override suspend fun delete(path: SAFPath, recursive: Boolean) = runIO {

        log(TAG, VERBOSE) { "delete(recursive=$recursive): $path" }

        // Every failure is a failed delete, including the lookups and enumerations it needs.
        // Callers like CorpseFinder only handle WriteException, a ReadException escaping from here
        // would abort their whole run instead of just this target.
        try {
            val target = lookup(path)

            when {
                !target.isDirectory -> deleteDocument(target.docFile, path)

                recursive -> deleteTreeCascading(target)

                else -> {
                    // Best-effort refusal, see the APathGateway.delete contract: a child that appears
                    // after this check can still be taken by a provider that cascades.
                    val hasChildren = target.docFile.hasChildren()
                    currentCoroutineContext().ensureActive()

                    if (hasChildren) throw WriteException("Directory not empty", path)

                    deleteDocument(target.docFile, path)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "delete($path) failed: ${e.asLog()}" }
            throw if (e is WriteException) e else WriteException(path = path, cause = e)
        }
    }

    /**
     * Deletes a directory and everything below it.
     *
     * The fast path is a single delete on the directory itself: AOSP's `FileSystemProvider` removes
     * the whole subtree, and the platform allows (but does not promise) that. A provider that
     * refuses, fails or only gets part way through falls back to a post-order walk, where every
     * directory is already empty by the time it is deleted.
     */
    private suspend fun deleteTreeCascading(target: SAFPathLookup) {
        currentCoroutineContext().ensureActive()

        val cascaded = try {
            target.docFile.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Cascading delete of ${target.lookedUp} failed: ${e.asLog()}" }
            false
        }

        currentCoroutineContext().ensureActive()

        // Under a dry-run SAFDocFile.delete deletes nothing and reports the document as still there,
        // which counts as a cascade here, so the fallback and its real deletions never run.
        if (cascaded) return

        if (!target.docFile.existsStrict()) {
            log(TAG, WARN) { "Tried to delete directory, but it's already gone: ${target.lookedUp}" }
            return
        }

        log(TAG, WARN) { "Provider didn't cascade ${target.lookedUp}, deleting children individually" }
        deleteTreePostOrder(target.docFile, target.lookedUp)
    }

    /**
     * Deletes [docFile] and everything below it, children before their parents.
     *
     * The walk runs on the [SAFDocFile]s the provider itself handed out. Document ids are opaque in
     * general, they can't be rebuilt from display names, and a provider whose ids aren't path derived
     * is exactly the kind that doesn't cascade, i.e. the only reason this fallback exists. [root] is
     * carried along for log and exception context only.
     */
    private suspend fun deleteTreePostOrder(docFile: SAFDocFile, root: SAFPath) {
        if (docFile.isDirectory) {
            docFile.listFiles().forEach { deleteTreePostOrder(it, root) }
        }
        deleteDocument(docFile, root)
    }

    private suspend fun deleteDocument(docFile: SAFDocFile, path: SAFPath) {
        currentCoroutineContext().ensureActive()

        if (docFile.delete()) return

        // Providers report a failure for a document that is already gone, but only a query that
        // actually answers may turn that into a success.
        if (!docFile.existsStrict()) {
            log(TAG, WARN) { "Tried to delete, but it's already gone: $path" }
            return
        }

        throw IOException("Document delete() call returned false")
    }

    override suspend fun canWrite(path: SAFPath): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "canWrite(): $path -> $docFile" }
            docFile.writable
        } catch (e: MissingUriPermissionException) {
            false
        } catch (e: Exception) {
            throw ReadException(path = path, cause = e)
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
            throw ReadException(path = path, cause = e)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "lookup($path) failed." }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun lookupExtended(path: SAFPath): SAFPathLookupExtended = runIO {
        try {
            SAFPathLookupExtended(lookup(path))
        } catch (e: Exception) {
            log(TAG, WARN) { "lookupExtended($path) failed." }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun lookupFiles(path: SAFPath): Flow<SAFPathLookup> = runIO {
        val children = childPaths(path, "lookupFiles")
        flow {
            children.forEach { emit(lookup(it)) }
        }
            .trace("lookupFiles($path)")
            .refineErrors("lookupFiles", path)
            .flowOn(dispatcherProvider.IO)
    }

    override suspend fun lookupFilesExtended(path: SAFPath): Flow<SAFPathLookupExtended> = runIO {
        val children = childPaths(path, "lookupFilesExtended")
        flow {
            children.forEach { emit(SAFPathLookupExtended(lookup(it))) }
        }
            .trace("lookupFilesExtended($path)")
            .refineErrors("lookupFilesExtended", path)
            .flowOn(dispatcherProvider.IO)
    }

    /**
     * The ContentResolver cursor enumeration can't be streamed, so the child listing is a snapshot
     * taken at call time. The per-child lookups on top of it stay lazy.
     */
    private fun childPaths(path: SAFPath, label: String): List<SAFPath> = try {
        val docFile = findDocFile(path)
        log(TAG, VERBOSE) { "$label($path) -> $docFile" }
        docFile.listFiles().map {
            val name = it.name ?: it.uri.pathSegments.last().split('/').last()
            path.child(name)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "$label($path) failed." }
        throw ReadException(path = path, cause = e)
    }

    private fun <T> Flow<T>.trace(label: String): Flow<T> = flow {
        if (!Bugs.isTrace) {
            emitAll(this@trace)
            return@flow
        }
        var count = 0
        emitAll(
            onEach { item ->
                count++
                log(TAG, VERBOSE) { "$label #$count $item" }
            }.onCompletion { cause ->
                if (cause == null) log(TAG, VERBOSE) { "$label finished $count items" }
                else log(TAG, VERBOSE) { "$label aborted after $count items: $cause" }
            }
        )
    }

    private fun <T> Flow<T>.refineErrors(label: String, path: SAFPath): Flow<T> = catch { e ->
        log(TAG, WARN) { "$label($path) failed." }
        when (e) {
            is CancellationException -> throw e
            is ReadException -> throw e
            else -> throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun walk(
        path: SAFPath,
        options: APathGateway.WalkOptions<SAFPath, SAFPathLookup>,
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
                lookupFiles(lookUp.lookedUp).toList()
            } catch (e: IOException) {
                log(TAG, ERROR) { "Failed to read $lookUp: $e" }
                if (options.onError?.invoke(lookUp, e) != false) {
                    emptyList()
                } else {
                    throw e
                }
            }

            newBatch
                .filter { child ->
                    // Same semantic as the local gateway's DirectLocalWalker filter
                    val excluded = options.pathDoesNotContain?.any { child.path.contains(it) } == true
                    if (Bugs.isTrace) {
                        if (excluded) log(TAG, VERBOSE) { "Skipping (pathDoesNotContain): $child" }
                    }
                    !excluded
                }
                .filter {
                    val allowed = options.onFilter?.invoke(it) ?: true
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
            throw ReadException(path = path, cause = e)
        }


    override suspend fun du(
        path: SAFPath,
        options: APathGateway.DuOptions<SAFPath, SAFPathLookup>,
    ): Long = runIO {
        try {
            val start = lookup(path)
            log(TAG, VERBOSE) { "du($path) -> $start" }

            if (start.isFile) return@runIO start.size

            var total = start.size

            val queue = LinkedList(listOf(start))
            while (!queue.isEmpty()) {
                val lookUp = queue.removeFirst()

                val newBatch = try {
                    lookupFiles(lookUp.lookedUp).toList()
                } catch (e: IOException) {
                    log(TAG, ERROR) { "Failed to read $lookUp: $e" }
                    if (options.abortOnError) throw e
                    emptyList()
                }

                newBatch.forEach { child ->
                    total += child.size
                    if (child.isDirectory) queue.addFirst(child)
                }
            }

            total
        } catch (e: Exception) {
            log(TAG, WARN) { "du($path) failed." }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun file(path: SAFPath, readWrite: Boolean): FileHandle = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "file(readWrite0$readWrite): $path -> $docFile" }

            if (readWrite && !docFile.writable) throw IOException("writable=false")
            else if (!docFile.readable) throw IOException("readable=false")

            val pfd = docFile.openPFD(contentResolver, if (readWrite) FileMode.READ_WRITE else FileMode.READ)
            pfd.toFileHandle(readWrite)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to access from $path: ${e.asLog()}" }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun setModifiedAt(path: SAFPath, modifiedAt: Instant): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "setModifiedAt(): $path -> $docFile" }
            docFile.setLastModified(modifiedAt)
        } catch (e: Exception) {
            throw WriteException(path = path, cause = e)
        }
    }

    override suspend fun setPermissions(path: SAFPath, permissions: Permissions): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "setPermissions(): $path -> $docFile" }
            docFile.setPermissions(permissions)
        } catch (e: Exception) {
            throw WriteException(path = path, cause = e)
        }
    }

    override suspend fun setOwnership(path: SAFPath, ownership: Ownership): Boolean = runIO {
        try {
            val docFile = findDocFile(path)
            log(TAG, VERBOSE) { "setOwnership(): $path -> $docFile" }
            docFile.setOwnership(ownership)
        } catch (e: Exception) {
            throw WriteException(path = path, cause = e)
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