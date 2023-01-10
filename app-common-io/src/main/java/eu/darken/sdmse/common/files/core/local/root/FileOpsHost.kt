package eu.darken.sdmse.common.files.core.local.root

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.Ownership
import eu.darken.sdmse.common.files.core.Permissions
import eu.darken.sdmse.common.files.core.asFile
import eu.darken.sdmse.common.files.core.local.*
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.shell.RootProcessShell
import eu.darken.sdmse.common.shell.SharedShell
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class FileOpsHost @Inject constructor(
    @RootProcessShell private val sharedShell: SharedShell,
    private val libcoreTool: LibcoreTool,
    private val ipcFunnel: IPCFunnel
) : FileOpsConnection.Stub() {

    override fun lookUp(path: LocalPath): LocalPathLookup = try {
        path.performLookup(ipcFunnel, libcoreTool)
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookUp(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun listFiles(path: LocalPath): List<LocalPath> = try {
        path.asFile().listFiles2().map { LocalPath.build(it) }
    } catch (e: Exception) {
        log(TAG, ERROR) { "listFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun listFilesStream(path: LocalPath): RemoteInputStream = try {
        val result = path.asFile().listFiles2().map { LocalPath.build(it) }
        if (Bugs.isTrace) log(TAG, VERBOSE) { "listFilesStream($path) ${result.size} items read, now streaming" }
        result.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun lookupFiles(path: LocalPath): List<LocalPathLookup> = try {
        listFiles(path).map { lookUp(it) }
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun lookupFilesStream(path: LocalPath): RemoteInputStream = try {
        val paths = listFiles(path)
        val lookups = paths.mapIndexed { index, item ->
            if (Bugs.isTrace) log(TAG, VERBOSE) { "Looking up $index: $item" }
            lookUp(item)
        }
        lookups.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun readFile(path: LocalPath): RemoteInputStream = try {
        FileInputStream(path.asFile()).remoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "readFile(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun writeFile(path: LocalPath): RemoteOutputStream = try {
        FileOutputStream(path.asFile()).toRemoteOutputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "writeFile(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun mkdirs(path: LocalPath): Boolean = try {
        path.asFile().mkdirs()
    } catch (e: Exception) {
        log(TAG, ERROR) { "mkdirs(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun createNewFile(path: LocalPath): Boolean = try {
        val file = path.asFile()

        if (file.exists() && file.isDirectory) {
            throw IllegalStateException("Can't create file, path exists and is directory: $path")
        }

        file.parentFile?.let {
            if (!it.exists()) {
                if (!it.mkdirs()) {
                    log(TAG, WARN) { "Failed to create parents for $path" }
                }
            }
        }

        file.createNewFile()
    } catch (e: Exception) {
        log(TAG, ERROR) { "mkdirs(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun canRead(path: LocalPath): Boolean = try {
        path.asFile().canRead()
    } catch (e: Exception) {
        log(TAG, ERROR) { "path(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun canWrite(path: LocalPath): Boolean = try {
        path.asFile().canWrite()
    } catch (e: Exception) {
        log(TAG, ERROR) { "canWrite(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun exists(path: LocalPath): Boolean = try {
        path.asFile().exists()
    } catch (e: Exception) {
        log(TAG, ERROR) { "exists(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun delete(path: LocalPath): Boolean = try {
        path.asFile().delete()
    } catch (e: Exception) {
        log(TAG, ERROR) { "delete(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean = try {
        linkPath.asFile().createSymlink(targetPath.asFile())
    } catch (e: Exception) {
        log(TAG, ERROR) { "createSymlink(linkPath=$linkPath, targetPath=$targetPath) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun setModifiedAt(path: LocalPath, modifiedAt: Long): Boolean = try {
        path.asFile().setLastModified(modifiedAt)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, modifiedAt=$modifiedAt) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun setPermissions(path: LocalPath, permissions: Permissions): Boolean = try {
        path.asFile().setPermissions(permissions)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, permissions=$permissions) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = try {
        path.asFile().setOwnership(ownership)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, ownership=$ownership) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    private fun wrapPropagating(e: Exception): Exception {
        return if (e is UnsupportedOperationException) e
        else UnsupportedOperationException(e)
    }

    companion object {
        val TAG = logTag("Root", "Java", "FileOps", "Host")
    }
}