package eu.darken.sdmse.common.files.core.local

import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.files.core.local.root.FileOpsClient
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import eu.darken.sdmse.common.root.javaroot.RootUnavailableException
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.storage.StorageEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class LocalGateway @Inject constructor(
    private val javaRootClient: JavaRootClient,
    private val ipcFunnel: IPCFunnel,
    private val libcoreTool: LibcoreTool,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val storageEnvironment: StorageEnvironment,
) : APathGateway<LocalPath, LocalPathLookup> {

    // Represents the resource that keeps the gateway resources alive
    // Internal resources should add themselfes as child to this
    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> rootOps(action: suspend (FileOpsClient) -> T): T {
        javaRootClient.addParent(this)
        return javaRootClient.runModuleAction(FileOpsClient::class.java) { action(it) }
    }

    private var rootCheckValue = 0
    private val rootCheckLock = Mutex()

    suspend fun hasRoot(): Boolean = rootCheckLock.withLock {
        if (rootCheckValue != 0) return@withLock rootCheckValue == 1

        rootCheckValue = try {
            javaRootClient.runSessionAction { it.ipc.checkBase() }
            log(TAG, INFO) { "Root is available." }
            1
        } catch (e: RootUnavailableException) {
            log(TAG, INFO) { "Root is NOT available." }
            -1
        }

        return@withLock rootCheckValue == 1
    }

    private val sharedUserShell = SharedShell(TAG, appScope + dispatcherProvider.IO)
    private suspend fun getShellSession(): Resource<RxCmdShell.Session> {
        return sharedUserShell.session.addParent(this).get()
    }

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
                    throw IllegalStateException("Item exists already, but it's a directory.")
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
                    if (!canRead) throw ReadException(path)
                    getShellSession().use { sessionResource ->
                        path.performLookup(ipcFunnel, libcoreTool, sessionResource.item)
                    }
                }
                hasRoot() && (mode == Mode.ROOT || !canRead && mode == Mode.AUTO) -> {
                    rootOps { it.lookUp(path) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: Exception) {
            throw ReadException(path, cause = e).also {
                log(TAG, WARN) { "lookup(path=$path, mode=$mode) failed:\n${it.asLog()}" }
            }
        }
    }

    override suspend fun listFiles(path: LocalPath): List<LocalPath> = listFiles(path, Mode.AUTO)

    suspend fun listFiles(path: LocalPath, mode: Mode = Mode.AUTO): List<LocalPath> = runIO {
        try {
            val nonRootList: List<File>? = try {
                when (mode) {
                    Mode.ROOT -> null
                    else -> path.asFile().listFiles2()
                }
            } catch (e: Exception) {
                null
            }

            when {
                mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                    if (nonRootList == null) throw ReadException(path)
                    nonRootList.map { LocalPath.build(it) }
                }
                hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                    rootOps { it.listFiles(path) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            throw ReadException(path, cause = e).also {
                log(TAG, WARN) { "listFiles(path=$path, mode=$mode) failed:\n${it.asLog()}" }
            }
        }
    }

    override suspend fun lookupFiles(path: LocalPath): List<LocalPathLookup> = lookupFiles(path, Mode.AUTO)

    suspend fun lookupFiles(path: LocalPath, mode: Mode = Mode.AUTO): List<LocalPathLookup> = withContext(
        dispatcherProvider.IO
    ) {
        try {
            val nonRootList = try {
                when (mode) {
                    Mode.ROOT -> null
                    else -> path.asFile().listFiles2()
                }
            } catch (e: Exception) {
                null
            }?.map { it.toLocalPath() }

            when {
                mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                    if (nonRootList == null) throw ReadException(path)
                    getShellSession().use { sessionResource ->
                        nonRootList.map { it.performLookup(ipcFunnel, libcoreTool, sessionResource.item) }
                    }
                }
                hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                    rootOps { it.lookupFiles(path) }
                }
                else -> throw IOException("No matching mode available.")
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
                    javaFile.exists()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canAccessParent) -> {
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
                    canNormalWrite
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
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
                    canNormalOpen
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalOpen) -> {
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
                    javaFile.source()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalOpen) -> {
                    val resource = javaRootClient.get()
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
                    file.sink()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canOpen) -> {
                    val resource = javaRootClient.get()
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

    override suspend fun delete(path: LocalPath): Boolean = delete(path, Mode.AUTO)

    suspend fun delete(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val javaFile = path.asFile()
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> javaFile.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    if (!canNormalWrite) throw WriteException(path)
                    javaFile.delete()
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    rootOps { it.delete(path) }
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
                    linkPathJava.createSymlink(targetPathJava)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
                    rootOps { it.createSymlink(linkPath, targetPath) }
                }
                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w("createSymlink(linkPath=%s, targetPath=%s, mode=%s) failed.", linkPath, targetPath, mode)
            throw WriteException(linkPath, cause = e)
        }
    }

    override suspend fun setModifiedAt(path: LocalPath, modifiedAt: Date): Boolean = setModifiedAt(
        path,
        modifiedAt,
        Mode.AUTO
    )

    suspend fun setModifiedAt(path: LocalPath, modifiedAt: Date, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                else -> path.file.canWrite()
            }
            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    path.file.setLastModified(modifiedAt.time)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
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
                    path.file.setPermissions(permissions)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
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
                    path.file.setOwnership(ownership)
                }
                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO && !canNormalWrite) -> {
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