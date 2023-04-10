package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.root.FileOpsClient
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.storage.StorageEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class LocalGateway @Inject constructor(
    private val rootServiceClient: RootServiceClient,
    private val ipcFunnel: IPCFunnel,
    private val libcoreTool: LibcoreTool,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val storageEnvironment: StorageEnvironment,
    private val rootManager: RootManager,
) : APathGateway<LocalPath, LocalPathLookup> {

    // Represents the resource that keeps the gateway resources alive
    // Internal resources should add themselfes as child to this
    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> rootOps(action: suspend (FileOpsClient) -> T): T {
        return rootServiceClient.runModuleAction(FileOpsClient::class.java) { action(it) }
    }

    suspend fun hasRoot(): Boolean = rootManager.useRoot()

    private suspend fun <T> runIO(
        block: suspend CoroutineScope.() -> T
    ): T = withContext(dispatcherProvider.IO) {
        block()
    }

    override suspend fun createDir(path: LocalPath): Boolean = createDir(path, Mode.AUTO)

    suspend fun createDir(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val file = path.asFile()

            if (mode == Mode.NORMAL || mode == Mode.AUTO) {
                if (file.mkdirs()) return@runIO true
                if (file.exists()) return@runIO false
            }

            if (hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO)) {
                rootOps { it.mkdirs(path) }
            } else {
                false
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("createDir(path=%s, mode=%s) failed.", path, mode)
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun createFile(path: LocalPath): Boolean = createFile(path, Mode.AUTO)

    suspend fun createFile(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            if (mode == Mode.NORMAL || mode == Mode.AUTO) {
                val file = path.asFile()

                if (file.exists() && file.isDirectory) {
                    throw IOException("Item exists already, but it's a directory.")
                }

                file.parentFile?.let {
                    if (!it.exists()) {
                        if (!it.mkdirs()) {
                            log(TAG, WARN) { "Failed to create parent directory $it for $path" }
                        }
                    }
                }

                if (file.createNewFile()) return@runIO true
                if (file.exists()) return@runIO false
            }

            if (hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO)) {
                rootOps { it.createNewFile(path) }
            } else {
                false
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("createFile(path=%s, mode=%s) failed.", path, mode)
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun lookup(path: LocalPath): LocalPathLookup = lookup(path, Mode.AUTO)

    suspend fun lookup(path: LocalPath, mode: Mode = Mode.AUTO): LocalPathLookup = runIO {
        try {
            val javaFile = path.asFile()
            val canRead = if (mode == Mode.ROOT) {
                false
            } else {
                javaFile.canRead()
            }

            when {
                mode == Mode.NORMAL || canRead && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "lookup($mode->NORMAL): $path" }
                    if (!canRead) throw ReadException(path)
                    path.performLookup(ipcFunnel, libcoreTool)
                }
                hasRoot() && (mode == Mode.ROOT || !canRead && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "lookup($mode->ROOT): $path" }
                    rootOps { it.lookUp(path) }
                }
                else -> throw IOException("No matching mode available.")
            }.also {
                log(TAG, VERBOSE) { "Looked up: $it" }
            }

        } catch (e: Exception) {
            throw ReadException(path, cause = e).also {
                log(TAG, WARN) { "lookup(path=$path, mode=$mode) failed:\n${it.asLog()}" }
            }
        }
    }

    override suspend fun listFiles(path: LocalPath): Collection<LocalPath> = listFiles(path, Mode.AUTO)

    suspend fun listFiles(path: LocalPath, mode: Mode = Mode.AUTO): Collection<LocalPath> = runIO {
        try {
            val javaFile = path.asFile()
            val nonRootList: List<File>? = try {
                when (mode) {
                    Mode.ROOT -> null
                    else -> if (javaFile.canRead()) javaFile.listFiles2() else null
                }
            } catch (e: Exception) {
                null
            }

            when {
                mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "listFiles($mode->NORMAL): $path" }
                    if (nonRootList == null) throw ReadException(path)
                    nonRootList.map { LocalPath.build(it) }
                }
                hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "listFiles($mode->ROOT): $path" }
                    rootOps { it.listFilesStream(path) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            throw ReadException(path, cause = e).also {
                log(TAG, WARN) { "listFiles(path=$path, mode=$mode) failed:\n${it.asLog()}" }
            }
        }
    }

    override suspend fun lookupFiles(path: LocalPath): Collection<LocalPathLookup> = lookupFiles(path, Mode.AUTO)

    suspend fun lookupFiles(path: LocalPath, mode: Mode = Mode.ROOT): Collection<LocalPathLookup> = runIO {
        try {
            val javaFile = path.asFile()
            val nonRootList = try {
                when (mode) {
                    Mode.ROOT -> null
                    else -> if (javaFile.canRead()) javaFile.listFiles2() else null
                }
            } catch (e: Exception) {
                null
            }

            when {
                mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "lookupFiles($mode->NORMAL): $path" }
                    if (nonRootList == null) throw ReadException(path)
                    nonRootList
                        .filter { it.canRead() }
                        .map { it.toLocalPath() }
                        .map { it.performLookup(ipcFunnel, libcoreTool) }
                }
                hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "lookupFiles($mode->ROOT): $path" }
                    rootOps { it.lookupFilesStream(path) }
                }
                else -> throw IOException("No matching mode available.")
            }.also {
                if (Bugs.isTrace) {
                    log(TAG, VERBOSE) { "Looked up ${it.size} items:" }
                    it.forEachIndexed { index, look -> log(TAG, VERBOSE) { "#$index $look" } }
                }
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "lookupFiles(path=$path, mode=$mode) failed:\n${e.asLog()}" }
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun exists(path: LocalPath): Boolean = exists(path, Mode.AUTO)

    suspend fun exists(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val javaFile = path.asFile()

            val canAccessParent = when (mode) {
                Mode.ROOT -> false
                else -> when {
                    javaFile.exists() -> true
                    javaFile.parentFile?.exists() == true -> true
                    else -> storageEnvironment.externalDirs
                        .map { it.asFile() }
                        .firstOrNull { javaFile.path.startsWith(it.path) }
                        ?.canRead() ?: false
                }
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canAccessParent -> {
                    log(TAG, VERBOSE) { "exists($mode->NORMAL): $path" }
                    javaFile.exists()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canAccessParent) -> {
                    log(TAG, VERBOSE) { "exists($mode->ROOT): $path" }
                    rootOps { it.exists(path) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "exists(path=$path, mode=$mode) failed:\n${e.asLog()}" }
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun canWrite(path: LocalPath): Boolean = canWrite(path, Mode.AUTO)

    suspend fun canWrite(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val file = path.asFile()
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> file.exists() && file.parentsInclusive.firstOrNull { it.exists() }?.canWrite() ?: false
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "canWrite($mode->NORMAL): $path" }
                    canNormalWrite
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    log(TAG, VERBOSE) { "canWrite($mode->ROOT): $path" }
                    rootOps { it.canWrite(path) }
                }
                else -> false
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("canWrite(path=%s, mode=%s) failed.", path, mode)
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun canRead(path: LocalPath): Boolean = canRead(path, Mode.AUTO)

    suspend fun canRead(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val file = path.asFile()
            val canNormalOpen = when (mode) {
                Mode.ROOT -> false
                else -> file.exists() && file.parentsInclusive.firstOrNull { it.exists() }?.isReadable() ?: false
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalOpen -> {
                    log(TAG, VERBOSE) { "canRead($mode->NORMAL): $path" }
                    canNormalOpen
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalOpen) -> {
                    log(TAG, VERBOSE) { "canRead($mode->ROOT): $path" }
                    rootOps { it.canRead(path) }
                }
                else -> false
            }

        } catch (e: IOException) {
            Timber.tag(TAG).w("canRead(path=%s, mode=%s) failed.", path, mode)
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun read(path: LocalPath): Source = read(path, Mode.AUTO)

    suspend fun read(path: LocalPath, mode: Mode = Mode.AUTO): Source = runIO {
        try {
            val javaFile = path.asFile()
            val canNormalOpen = when (mode) {
                Mode.ROOT -> false
                else -> javaFile.isReadable()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalOpen -> {
                    log(TAG, VERBOSE) { "read($mode->NORMAL): $path" }
                    javaFile.source()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalOpen) -> {
                    log(TAG, VERBOSE) { "read($mode->ROOT): $path" }
                    // We need to keep the resource alive until the caller is done with the Source object
                    val resource = rootServiceClient.get()
                    rootOps {
                        it.readFile(path).callbacks { resource.close() }
                    }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("read(path=%s, mode=%s) failed.", path, mode)
            throw ReadException(path, cause = e)
        }
    }

    override suspend fun write(path: LocalPath): Sink = write(path, Mode.AUTO)

    suspend fun write(path: LocalPath, mode: Mode = Mode.AUTO): Sink = runIO {
        try {
            val file = path.asFile()

            val canOpen = when (mode) {
                Mode.ROOT -> false
                else -> (file.exists() && file.canWrite()) || !file.exists() && file.parentFile?.canWrite() == true
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canOpen -> {
                    log(TAG, VERBOSE) { "write($mode->NORMAL): $path" }
                    file.sink()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canOpen) -> {
                    log(TAG, VERBOSE) { "write($mode->ROOT): $path" }
                    // We need to keep the resource alive until the caller is done with the Sink object
                    val resource = rootServiceClient.get()
                    rootOps {
                        it.writeFile(path).callbacks { resource.close() }
                    }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("write(path=%s, mode=%s) failed.", path, mode)
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun delete(path: LocalPath) = delete(path, Mode.AUTO)

    suspend fun delete(path: LocalPath, mode: Mode = Mode.AUTO): Unit = runIO {
        try {
            val javaFile = path.asFile()
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> javaFile.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "delete($mode->NORMAL): $path" }
                    if (!canNormalWrite) throw WriteException(path)

                    var success = if (Bugs.isDryRun) {
                        log(TAG, WARN) { "DRYRUN: Not deleting $javaFile" }
                        javaFile.exists()
                    } else {
                        javaFile.delete()
                    }

                    if (!success) {
                        success = !javaFile.exists()
                        if (success) log(TAG, WARN) { "Tried to delete file, but it's already gone: $path" }
                    }

                    if (!success) {
                        if (mode == Mode.AUTO && hasRoot()) {
                            delete(path, Mode.ROOT)
                            return@runIO
                        } else {
                            throw IOException("delete() call returned false")
                        }
                    }
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    log(TAG, VERBOSE) { "delete($mode->ROOT): $path" }
                    rootOps {
                        var success = if (Bugs.isDryRun) {
                            log(TAG, WARN) { "DRYRUN: Not deleting (root) $javaFile" }
                            it.exists(path)
                        } else {
                            it.delete(path)
                        }

                        if (!success) {
                            // TODO We could move this into the root service for better performance?
                            success = !it.exists(path)
                            if (success) log(TAG, WARN) { "Tried to delete file, but it's already gone: $path" }
                        }

                        if (!success) throw IOException("Root delete() call returned false")
                    }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("delete(path=%s, mode=%s) failed.", path, mode)
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean =
        createSymlink(linkPath, targetPath, Mode.AUTO)

    suspend fun createSymlink(linkPath: LocalPath, targetPath: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val linkPathJava = linkPath.asFile()
            val targetPathJava = targetPath.asFile()
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> linkPathJava.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "createSymlink($mode->NORMAL): $linkPath -> $targetPath" }
                    linkPathJava.createSymlink(targetPathJava)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    log(TAG, VERBOSE) { "createSymlink($mode->ROOT): $linkPath -> $targetPath" }
                    rootOps { it.createSymlink(linkPath, targetPath) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("createSymlink(linkPath=%s, targetPath=%s, mode=%s) failed.", linkPath, targetPath, mode)
            throw WriteException(linkPath, cause = e)
        }
    }

    override suspend fun setModifiedAt(path: LocalPath, modifiedAt: Instant): Boolean = setModifiedAt(
        path,
        modifiedAt,
        Mode.AUTO
    )

    suspend fun setModifiedAt(path: LocalPath, modifiedAt: Instant, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> path.file.canWrite()
            }
            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "setModifiedAt($mode->NORMAL): $path" }
                    path.file.setLastModified(modifiedAt.toEpochMilli())
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    log(TAG, VERBOSE) { "setModifiedAt($mode->ROOT): $path" }
                    rootOps {
                        it.setModifiedAt(path, modifiedAt)
                    }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("setModifiedAt(path=%s, modifiedAt=%s, mode=%s) failed.", path, modifiedAt, mode)
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun setPermissions(path: LocalPath, permissions: Permissions): Boolean =
        setPermissions(path, permissions, Mode.AUTO)

    suspend fun setPermissions(path: LocalPath, permissions: Permissions, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> path.file.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "setPermissions($mode->NORMAL): $path" }
                    path.file.setPermissions(permissions)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    log(TAG, VERBOSE) { "setPermissions($mode->ROOT): $path" }
                    rootOps { it.setPermissions(path, permissions) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("setPermissions(path=%s, permissions=%s, mode=%s) failed.", path, permissions, mode)
            throw WriteException(path, cause = e)
        }
    }

    override suspend fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = setOwnership(
        path,
        ownership,
        Mode.AUTO
    )

    suspend fun setOwnership(path: LocalPath, ownership: Ownership, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> path.file.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "setOwnership($mode->NORMAL): $path" }
                    path.file.setOwnership(ownership)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    log(TAG, VERBOSE) { "setOwnership($mode->ROOT): $path" }
                    rootOps { it.setOwnership(path, ownership) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("setOwnership(path=%s, ownership=%s, mode=%s) failed.", path, ownership, mode)
            throw WriteException(path, cause = e)
        }
    }

    enum class Mode {
        AUTO, NORMAL, ROOT
    }

    companion object {
        val TAG = logTag("Gateway", "Local")
    }
}