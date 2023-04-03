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
import java.io.IOException
import javax.inject.Inject

class FileOpsHost @Inject constructor(
    @RootProcessShell private val sharedShell: SharedShell,
    private val libcoreTool: LibcoreTool,
    private val ipcFunnel: IPCFunnel
) : FileOpsConnection.Stub() {

    override fun lookUp(path: LocalPath): LocalPathLookup = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookUp($path)..." }
        path.performLookup(ipcFunnel, libcoreTool).also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookUp($path): $it" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookUp(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun listFiles(path: LocalPath): List<LocalPath> = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "listFiles($path)..." }
        path.asFile().listFiles2()
            .map { LocalPath.build(it) }
            .onEach { if (Bugs.isTrace) log(TAG, VERBOSE) { "$it" } }
            .also { if (Bugs.isTrace) log(TAG, VERBOSE) { "listFiles($path) done: ${it.size} items" } }
    } catch (e: Exception) {
        log(TAG, ERROR) { "listFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun listFilesStream(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "listFilesStream($path)..." }
        val result = path.asFile().listFiles2().map { LocalPath.build(it) }
        if (Bugs.isTrace) log(TAG, VERBOSE) { "listFilesStream($path) ${result.size} items read, now streaming" }
        result.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun lookupFiles(path: LocalPath): List<LocalPathLookup> = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFiles($path)..." }
        listFiles(path)
            .mapIndexed { index, item ->
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Looking up $index: $item" }
                item.performLookup(ipcFunnel, libcoreTool)
            }
            .also { if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFiles($path) done: ${it.size} items" } }
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun lookupFilesStream(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFilesStream($path)..." }
        val paths = listFiles(path)
        val lookups = paths.mapIndexed { index, item ->
            if (Bugs.isTrace) log(TAG, VERBOSE) { "Looking up $index: $item" }
            item.performLookup(ipcFunnel, libcoreTool)
        }
        lookups.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun readFile(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "readFile($path)..." }
        FileInputStream(path.asFile()).remoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "readFile(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun writeFile(path: LocalPath): RemoteOutputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "writeFile($path)..." }
        FileOutputStream(path.asFile()).toRemoteOutputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "writeFile(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun mkdirs(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "mkdirs($path)..." }
        path.asFile().mkdirs()
    } catch (e: Exception) {
        log(TAG, ERROR) { "mkdirs(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun createNewFile(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "createNewFile($path)..." }
        val file = path.asFile()

        if (file.exists() && file.isDirectory) {
            throw IOException("Can't create file, path exists and is directory: $path")
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
        if (Bugs.isTrace) log(TAG, VERBOSE) { "canRead($path)..." }
        path.asFile().canRead()
    } catch (e: Exception) {
        log(TAG, ERROR) { "path(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun canWrite(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "canWrite($path)..." }
        path.asFile().canWrite()
    } catch (e: Exception) {
        log(TAG, ERROR) { "canWrite(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun exists(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "exists($path)..." }
        path.asFile().exists()
    } catch (e: Exception) {
        log(TAG, ERROR) { "exists(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun delete(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "exists($path)..." }
        path.asFile().delete()
    } catch (e: Exception) {
        log(TAG, ERROR) { "delete(path=$path) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "createSymlink($linkPath,$targetPath)..." }
        linkPath.asFile().createSymlink(targetPath.asFile())
    } catch (e: Exception) {
        log(TAG, ERROR) { "createSymlink(linkPath=$linkPath, targetPath=$targetPath) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun setModifiedAt(path: LocalPath, modifiedAt: Long): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "setModifiedAt($path,$modifiedAt)..." }
        path.asFile().setLastModified(modifiedAt)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, modifiedAt=$modifiedAt) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun setPermissions(path: LocalPath, permissions: Permissions): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "setPermissions($path,$permissions)..." }
        path.asFile().setPermissions(permissions)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, permissions=$permissions) failed\n${e.asLog()}" }
        throw wrapPropagating(e)
    }

    override fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "setPermissions($path,$ownership)..." }
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