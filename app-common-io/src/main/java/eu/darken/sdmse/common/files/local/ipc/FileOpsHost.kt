package eu.darken.sdmse.common.files.local.ipc

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.core.local.createSymlink
import eu.darken.sdmse.common.files.core.local.listFiles2
import eu.darken.sdmse.common.files.local.DirectLocalWalker
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.files.local.performLookup
import eu.darken.sdmse.common.files.local.performLookupExtended
import eu.darken.sdmse.common.files.local.setOwnership
import eu.darken.sdmse.common.files.local.setPermissions
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.ipc.IpcHostModule
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.RemoteOutputStream
import eu.darken.sdmse.common.ipc.remoteInputStream
import eu.darken.sdmse.common.ipc.toRemoteOutputStream
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class FileOpsHost @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val libcoreTool: LibcoreTool,
    private val ipcFunnel: IPCFunnel,
) : FileOpsConnection.Stub(), IpcHostModule {

    private fun listFiles(path: LocalPath): List<LocalPath> = path.asFile().listFiles2().map { LocalPath.build(it) }

    override fun listFilesStream(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "listFilesStream($path)..." }
        val result = path.asFile().listFiles2().map { LocalPath.build(it) }
        if (Bugs.isTrace) log(TAG, VERBOSE) { "listFilesStream($path) ${result.size} items read, now streaming" }
        result.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun lookUp(path: LocalPath): LocalPathLookup = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookUp($path)..." }
        path.performLookup().also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookUp($path): $it" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookUp(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun lookupFilesStream(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFilesStream($path)..." }
        val paths = listFiles(path)
        val lookups = paths.mapIndexed { index, item ->
            if (Bugs.isTrace) log(TAG, VERBOSE) { "Looking up $index: $item" }
            item.performLookup()
        }
        lookups.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFiles(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun lookUpExtended(path: LocalPath): LocalPathLookupExtended = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookUpExtended($path)..." }
        path.performLookupExtended(ipcFunnel, libcoreTool).also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookUpExtended($path): $it" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookUpExtended(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun lookupFilesExtended(path: LocalPath): List<LocalPathLookupExtended> = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFilesExtended($path)..." }
        listFiles(path)
            .mapIndexed { index, item ->
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Looking up extended $index: $item" }
                item.performLookupExtended(ipcFunnel, libcoreTool)
            }
            .also { if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFilesExtended($path) done: ${it.size} items" } }
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFilesExtended(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun walkStream(path: LocalPath, pathDoesNotContain: List<String>): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "walkStream($path)..." }
        runBlocking {
            DirectLocalWalker(
                start = path,
                onFilter = { lookup ->
                    pathDoesNotContain.none { lookup.path.contains(it) }
                },
            ).toRemoteInputStream(appScope + dispatcherProvider.IO)
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "walkStream(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun lookupFilesExtendedStream(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFilesExtendedStream($path)..." }
        val paths = listFiles(path)
        val lookups = paths.mapIndexed { index, item ->
            if (Bugs.isTrace) log(TAG, VERBOSE) { "Looking up extended $index: $item" }
            item.performLookupExtended(ipcFunnel, libcoreTool)
        }
        lookups.toRemoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "lookupFilesExtendedStream(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun du(path: LocalPath): Long = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "du($path)..." }
        runBlocking { path.asFile().walkTopDown().map { it.length() }.sum() }
    } catch (e: Exception) {
        log(TAG, ERROR) { "exists(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun readFile(path: LocalPath): RemoteInputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "readFile($path)..." }
        FileInputStream(path.asFile()).remoteInputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "readFile(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun writeFile(path: LocalPath): RemoteOutputStream = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "writeFile($path)..." }
        FileOutputStream(path.asFile()).toRemoteOutputStream()
    } catch (e: Exception) {
        log(TAG, ERROR) { "writeFile(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun mkdirs(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "mkdirs($path)..." }
        path.asFile().mkdirs()
    } catch (e: Exception) {
        log(TAG, ERROR) { "mkdirs(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
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
        throw e.wrapToPropagate()
    }

    override fun canRead(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "canRead($path)..." }
        path.asFile().canRead()
    } catch (e: Exception) {
        log(TAG, ERROR) { "path(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun canWrite(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "canWrite($path)..." }
        path.asFile().canWrite()
    } catch (e: Exception) {
        log(TAG, ERROR) { "canWrite(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun exists(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "exists($path)..." }
        path.asFile().exists()
    } catch (e: Exception) {
        log(TAG, ERROR) { "exists(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun delete(path: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "exists($path)..." }
        path.asFile().delete()
    } catch (e: Exception) {
        log(TAG, ERROR) { "delete(path=$path) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "createSymlink($linkPath,$targetPath)..." }
        linkPath.asFile().createSymlink(targetPath.asFile())
    } catch (e: Exception) {
        log(TAG, ERROR) { "createSymlink(linkPath=$linkPath, targetPath=$targetPath) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun setModifiedAt(path: LocalPath, modifiedAt: Long): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "setModifiedAt($path,$modifiedAt)..." }
        path.asFile().setLastModified(modifiedAt)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, modifiedAt=$modifiedAt) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun setPermissions(path: LocalPath, permissions: Permissions): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "setPermissions($path,$permissions)..." }
        path.asFile().setPermissions(permissions)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, permissions=$permissions) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    override fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = try {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "setPermissions($path,$ownership)..." }
        path.asFile().setOwnership(ownership)
    } catch (e: Exception) {
        log(TAG, ERROR) { "setModifiedAt(path=$path, ownership=$ownership) failed\n${e.asLog()}" }
        throw e.wrapToPropagate()
    }

    // Not all exception can be passed through the binder
    // See Parcel.writeException(...)
    private fun wrapPropagating(e: Exception): Exception {
        return if (e is RuntimeException) e else RuntimeException(e)
    }

    companion object {
        val TAG = logTag("FileOps", "Service", "Host", Bugs.processTag)
    }
}