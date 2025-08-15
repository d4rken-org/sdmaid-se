package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.AdbUnavailableException
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.adb.service.runModuleAction
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
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
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.callbacks
import eu.darken.sdmse.common.files.core.local.createSymlink
import eu.darken.sdmse.common.files.core.local.isReadable
import eu.darken.sdmse.common.files.core.local.listFiles2
import eu.darken.sdmse.common.files.core.local.parentsInclusive
import eu.darken.sdmse.common.files.local.ipc.FileOpsClient
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.ipc.fileHandle
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.root.service.runModuleAction
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourcesAlive
import eu.darken.sdmse.common.storage.StorageEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.FileHandle
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class LocalGateway @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val libcoreTool: LibcoreTool,
    @param:AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val storageEnvironment: StorageEnvironment,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
) : APathGateway<LocalPath, LocalPathLookup, LocalPathLookupExtended> {

    // Represents the resource that keeps the gateway resources alive
    // Internal resources should add themselfes as child to this
    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private suspend fun <T> rootOps(action: suspend (FileOpsClient) -> T): T {
        if (!rootManager.canUseRootNow()) throw RootUnavailableException()
        return keepResourcesAlive(rootManager.serviceClient) {
            rootManager.serviceClient.runModuleAction(FileOpsClient::class.java) { action(it) }
        }
    }

    private suspend fun <T> adbOps(action: suspend (FileOpsClient) -> T): T {
        if (!adbManager.canUseAdbNow()) throw AdbUnavailableException()
        return keepResourcesAlive(adbManager.serviceClient) {
            adbManager.serviceClient.runModuleAction(FileOpsClient::class.java) { action(it) }
        }
    }

    suspend fun hasRoot(): Boolean = rootManager.canUseRootNow()

    suspend fun hasAdb(): Boolean = adbManager.canUseAdbNow()

    private suspend fun <T> runIO(
        block: suspend CoroutineScope.() -> T
    ): T = withContext(dispatcherProvider.IO) {
        block()
    }

    override suspend fun createDir(path: LocalPath): Unit = createDir(path, Mode.AUTO)

    suspend fun createDir(path: LocalPath, mode: Mode = Mode.AUTO): Unit = runIO {
        try {
            val file = path.asFile()

            if (mode == Mode.NORMAL || mode == Mode.AUTO) {
                if (file.exists() && file.isFile) {
                    throw IOException(" Path exists already, but it's a file")
                }

                if (file.mkdirs()) {
                    log(TAG, VERBOSE) { "createDir($mode->NORMAL): $path" }
                    return@runIO
                }
                if (file.exists()) {
                    log(TAG, WARN) { "createDir(NORMAL) failed, but dir does now exist: $path" }
                    return@runIO
                }
            }

            if (hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO)) {
                if (rootOps { file.exists() && file.isFile }) {
                    throw IOException("Path exists already, but it's a file.")
                }
                if (rootOps { it.mkdirs(path) }) {
                    log(TAG, VERBOSE) { "createDir($mode->ROOT): $path" }
                    return@runIO
                }
                if (rootOps { it.exists(path) }) {
                    log(TAG, WARN) { "createDir(ROOT) failed, but dir does now exist: $path" }
                    return@runIO
                }
            }

            if (hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO)) {
                if (adbOps { file.exists() && file.isFile }) {
                    throw IOException("Path exists already, but it's a file.")
                }
                if (adbOps { it.mkdirs(path) }) {
                    log(TAG, VERBOSE) { "createDir($mode->ADB): $path" }
                    return@runIO
                }
                if (adbOps { it.exists(path) }) {
                    log(TAG, WARN) { "createDir(ADB) failed, but dir does now exist: $path" }
                    return@runIO
                }
            }

            throw IOException("No matching mode available.")
        } catch (e: IOException) {
            log(TAG, WARN) { "createDir(path=$path, mode=$mode) failed." }
            throw WriteException(path = path, cause = e)
        }
    }

    override suspend fun createFile(path: LocalPath): Unit = createFile(path, Mode.AUTO)

    suspend fun createFile(path: LocalPath, mode: Mode = Mode.AUTO): Unit = runIO {
        try {
            if (mode == Mode.NORMAL || mode == Mode.AUTO) {
                val file = path.asFile()

                if (file.exists()) {
                    throw IOException("Path exists already")
                }

                file.parentFile?.let {
                    if (!it.exists()) {
                        if (!it.mkdirs()) {
                            log(TAG, WARN) { "Failed to create parent directory $it for $path" }
                        }
                    }
                }

                if (file.createNewFile()) {
                    log(TAG, VERBOSE) { "createFile($mode->NORMAL): $path" }
                    return@runIO
                }
                if (file.exists()) {
                    log(TAG, WARN) { "createFile(NORMAL) failed, but file does now exist: $path" }
                    return@runIO
                }
            }

            if (hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO)) {
                if (rootOps { it.exists(path) }) {
                    throw IOException("Path exists already")
                }
                if (rootOps { it.createNewFile(path) }) {
                    log(TAG, VERBOSE) { "createFile($mode->ROOT): $path" }
                    return@runIO
                }
                if (rootOps { it.exists(path) }) {
                    log(TAG, WARN) { "createFile(ROOT) failed, but file does now exist: $path" }
                    return@runIO
                }
            }

            if (hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO)) {
                if (adbOps { it.exists(path) }) {
                    throw IOException("Path exists already")
                }
                if (adbOps { it.createNewFile(path) }) {
                    log(TAG, VERBOSE) { "createFile($mode->ADB): $path" }
                    return@runIO
                }
                if (adbOps { it.exists(path) }) {
                    log(TAG, WARN) { "createFile(ADB) failed, but file does now exist: $path" }
                    return@runIO
                }
            }

            throw IOException("No matching mode available.")
        } catch (e: IOException) {
            log(TAG, WARN) { "createFile(path=$path, mode=$mode) failed." }
            throw WriteException(path = path, cause = e)
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
                    if (!canRead) throw ReadException(path = path)
                    path.performLookup()
                }

                hasRoot() && (mode == Mode.ROOT || !canRead && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "lookup($mode->ROOT): $path" }
                    rootOps { it.lookUp(path) }
                }

                hasAdb() && (mode == Mode.ADB || !canRead && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "lookup($mode->ADB): $path" }
                    adbOps { it.lookUp(path) }
                }

                else -> throw IOException("No matching mode available.")
            }.also {
                log(TAG, VERBOSE) { "Looked up: $it" }
            }

        } catch (e: Exception) {
            throw ReadException(path = path, cause = e).also {
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
                    Mode.ADB -> null
                    else -> if (javaFile.canRead()) javaFile.listFiles2() else null
                }
            } catch (e: Exception) {
                log(TAG) { "listFiles($path, $mode) failed: $e" }
                null
            }

            when {
                mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "listFiles($mode->NORMAL): $path" }
                    if (nonRootList == null) throw ReadException(path = path)
                    nonRootList.map { LocalPath.build(it) }
                }

                hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "listFiles($mode->ROOT): $path" }
                    rootOps { it.listFiles(path) }
                }

                hasAdb() && (mode == Mode.ADB || nonRootList == null && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "listFiles($mode->ADB): $path" }
                    adbOps { it.listFiles(path) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            throw ReadException(path = path, cause = e).also {
                log(TAG, WARN) { "listFiles(path=$path, mode=$mode) failed:\n${it.asLog()}" }
            }
        }
    }

    override suspend fun lookupFiles(path: LocalPath): Collection<LocalPathLookup> = lookupFiles(path, Mode.AUTO)

    suspend fun lookupFiles(path: LocalPath, mode: Mode = Mode.AUTO): Collection<LocalPathLookup> = runIO {
        try {
            val javaFile = path.asFile()
            val nonRootList = try {
                when (mode) {
                    Mode.ROOT -> null
                    Mode.ADB -> null
                    else -> if (javaFile.canRead()) javaFile.listFiles2() else null
                }
            } catch (e: Exception) {
                null
            }

            when {
                mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "lookupFiles($mode->NORMAL): $path" }
                    if (nonRootList == null) throw ReadException(path = path)
                    nonRootList
                        .map { it.toLocalPath() }
                        .map { it.performLookup() }
                }

                hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "lookupFiles($mode->ROOT): $path" }
                    rootOps { it.lookupFiles(path) }
                }

                hasAdb() && (mode == Mode.ADB || nonRootList == null && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "lookupFiles($mode->ADB): $path" }
                    adbOps { it.lookupFiles(path) }
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
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun lookupFilesExtended(
        path: LocalPath
    ): Collection<LocalPathLookupExtended> = lookupFilesExtended(path, Mode.AUTO)

    suspend fun lookupFilesExtended(path: LocalPath, mode: Mode = Mode.AUTO): Collection<LocalPathLookupExtended> =
        runIO {
            try {
                val javaFile = path.asFile()
                val nonRootList = try {
                    when (mode) {
                        Mode.ROOT -> null
                        Mode.ADB -> null
                        else -> if (javaFile.canRead()) javaFile.listFiles2() else null
                    }
                } catch (e: Exception) {
                    null
                }

                when {
                    mode == Mode.NORMAL || nonRootList != null && mode == Mode.AUTO -> {
                        log(TAG, VERBOSE) { "lookupFilesExtended($mode->NORMAL): $path" }
                        if (nonRootList == null) throw ReadException(path = path)
                        nonRootList
                            .map { it.toLocalPath() }
                            .map { it.performLookupExtended(ipcFunnel, libcoreTool) }
                    }

                    hasRoot() && (mode == Mode.ROOT || nonRootList == null && mode == Mode.AUTO) -> {
                        log(TAG, VERBOSE) { "lookupFilesExtended($mode->ROOT): $path" }
                        rootOps { it.lookupFilesExtendedStream(path) }
                    }

                    hasAdb() && (mode == Mode.ADB || nonRootList == null && mode == Mode.AUTO) -> {
                        log(TAG, VERBOSE) { "lookupFilesExtended($mode->ADB): $path" }
                        adbOps { it.lookupFilesExtendedStream(path) }
                    }

                    else -> throw IOException("No matching mode available.")
                }.also {
                    if (Bugs.isTrace) {
                        log(TAG, VERBOSE) { "Looked up ${it.size} items:" }
                        it.forEachIndexed { index, look -> log(TAG, VERBOSE) { "#$index $look" } }
                    }
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "lookupFilesExtended(path=$path, mode=$mode) failed:\n${e.asLog()}" }
                throw ReadException(path = path, cause = e)
            }
        }

    override suspend fun walk(
        path: LocalPath,
        options: APathGateway.WalkOptions<LocalPath, LocalPathLookup>,
    ): Flow<LocalPathLookup> = walk(path, options, Mode.AUTO)

    suspend fun walk(
        path: LocalPath,
        options: APathGateway.WalkOptions<LocalPath, LocalPathLookup>,
        mode: Mode = Mode.AUTO,
    ): Flow<LocalPathLookup> = runIO {
        try {
            val javaFile = path.asFile()
            val canRead = when (mode) {
                Mode.AUTO, Mode.NORMAL -> if (javaFile.canRead()) {
                    try {
                        javaFile.listFiles2()
                        true
                    } catch (e: IOException) {
                        false
                    }
                } else {
                    false
                }

                else -> false
            }

            when {
                mode == Mode.NORMAL -> {
                    log(TAG, VERBOSE) { "walk($mode->NORMAL, direct): $path" }
                    if (!canRead) throw ReadException(path = path)
                    DirectLocalWalker(
                        start = path,
                        onFilter = { lookup -> options.onFilter?.invoke(lookup) ?: true },
                        onError = { lookup, exception -> options.onError?.invoke(lookup, exception) ?: true },
                    )
                }

                canRead && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "walk($mode->NORMAL, escalating): $path" }
                    EscalatingWalker(
                        gateway = this@LocalGateway,
                        start = path,
                        options = options,
                    )
                }

                hasRoot() && (mode == Mode.ROOT || !canRead && mode == Mode.AUTO) -> {
                    if (options.isDirect) {
                        log(TAG, VERBOSE) { "walk($mode->ROOT, direct): $path" }
                        // We need to keep the resource alive until the caller is done with the Flow
                        val resource = rootManager.serviceClient.get()
                        rootOps { it.walk(path, options).onCompletion { resource.close() } }
                    } else {
                        log(TAG, VERBOSE) { "walk($mode->ROOT, indirect): $path" }
                        // Can't pass functions via IPC
                        IndirectLocalWalker(
                            gateway = this@LocalGateway,
                            mode = Mode.ROOT,
                            start = path,
                            onFilter = { lookup -> options.onFilter?.invoke(lookup) ?: true },
                            onError = { lookup, exception -> options.onError?.invoke(lookup, exception) ?: true },
                        )
                    }
                }

                hasAdb() && (mode == Mode.ADB || !canRead && mode == Mode.AUTO) -> {
                    if (options.isDirect) {
                        log(TAG, VERBOSE) { "walk($mode->ADB, direct): $path" }
                        // We need to keep the resource alive until the caller is done with the Flow
                        val resource = adbManager.serviceClient.get()
                        adbOps { it.walk(path, options).onCompletion { resource.close() } }
                    } else {
                        log(TAG, VERBOSE) { "walk($mode->ADB, indirect): $path" }
                        // Can't pass functions via IPC
                        IndirectLocalWalker(
                            gateway = this@LocalGateway,
                            mode = Mode.ADB,
                            start = path,
                            onFilter = { lookup -> options.onFilter?.invoke(lookup) ?: true },
                            onError = { lookup, exception -> options.onError?.invoke(lookup, exception) ?: true },
                        )
                    }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "walk(path=$path, mode=$mode) failed:\n${e.asLog()}" }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun du(
        path: LocalPath,
        options: APathGateway.DuOptions<LocalPath, LocalPathLookup>,
    ): Long = du(path, options, Mode.AUTO)

    suspend fun du(
        path: LocalPath,
        options: APathGateway.DuOptions<LocalPath, LocalPathLookup> = APathGateway.DuOptions(),
        mode: Mode = Mode.AUTO,
    ): Long = runIO {
        try {
            val javaFile = path.asFile()
            val canRead = when (mode) {
                Mode.AUTO, Mode.NORMAL -> if (javaFile.canRead()) {
                    try {
                        javaFile.length()
                        true
                    } catch (e: IOException) {
                        false
                    }
                } else {
                    false
                }

                else -> false
            }

            when {
                mode == Mode.NORMAL -> {
                    log(TAG, VERBOSE) { "walk($mode->NORMAL, direct): $path" }
                    if (!canRead) throw ReadException(path = path)
                    javaFile.walkTopDown().map { it.length() }.sum()
                }

                canRead && mode == Mode.AUTO -> {
                    log(TAG, VERBOSE) { "du($mode->AUTO, escalating): $path" }
                    try {
                        du(path, mode = Mode.NORMAL)
                    } catch (e: ReadException) {
                        when {
                            hasRoot() -> du(path, mode = Mode.ROOT)
                            hasAdb() -> du(path, mode = Mode.ADB)
                            else -> throw e
                        }
                    }
                }

                hasRoot() && (mode == Mode.ROOT || !canRead && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "du($mode->ROOT): $path" }
                    rootOps { it.du(path) }
                }

                hasAdb() && (mode == Mode.ADB || !canRead && mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "du($mode->ADB): $path" }
                    adbOps { it.du(path) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "du(path=$path, mode=$mode) failed:\n${e.asLog()}" }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun exists(path: LocalPath): Boolean = exists(path, Mode.AUTO)

    suspend fun exists(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val javaFile = path.asFile()
            val javaFileParent = javaFile.parentFile

            val canCheckNormal = when (mode) {
                Mode.ROOT -> false
                Mode.ADB -> false
                else -> when {
                    // exists() = true is never a false positive
                    javaFile.exists() -> true
                    // This is a bit iffy, but checking readability on the parent has proven reliable
                    javaFileParent?.exists() == true && javaFileParent.canRead() -> true
                    // On Android 12+ Android/data isn't accessible anymore via normal java file access.
                    hasApiLevel(32) && storageEnvironment.publicDataDirs.any { it.isAncestorOf(path) } -> false
                    // If the file path is on public storage, and it wasn't Android/data then, assume true
                    else -> storageEnvironment.externalDirs
                        .firstOrNull { it.isAncestorOf(path) }
                        ?.asFile()
                        ?.canRead() ?: false
                }
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canCheckNormal -> {
                    log(TAG, VERBOSE) { "exists($mode->NORMAL): $path" }
                    javaFile.exists()
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "exists($mode->ROOT): $path" }
                    rootOps { it.exists(path) }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "exists($mode->ADB): $path" }
                    adbOps { it.exists(path) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "exists(path=$path, mode=$mode) failed:\n${e.asLog()}" }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun canWrite(path: LocalPath): Boolean = canWrite(path, Mode.AUTO)

    suspend fun canWrite(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val file = path.asFile()
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                Mode.ADB -> false
                else -> file.exists() && file.parentsInclusive.firstOrNull { it.exists() }?.canWrite() ?: false
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "canWrite($mode->NORMAL): $path" }
                    canNormalWrite
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "canWrite($mode->ROOT): $path" }
                    rootOps { it.canWrite(path) }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "canWrite($mode->ADB): $path" }
                    adbOps { it.canWrite(path) }
                }

                else -> false
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "canWrite(path=$path, mode$mode) failed." }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun canRead(path: LocalPath): Boolean = canRead(path, Mode.AUTO)

    suspend fun canRead(path: LocalPath, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val file = path.asFile()
            val canNormalOpen = when (mode) {
                Mode.ROOT -> false
                Mode.ADB -> false
                // TODO This isn't a great way to check readability
                else -> file.exists() && file.parentsInclusive.firstOrNull { it.exists() }?.isReadable() ?: false
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalOpen -> {
                    log(TAG, VERBOSE) { "canRead($mode->NORMAL): $path" }
                    canNormalOpen
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "canRead($mode->ROOT): $path" }
                    rootOps { it.canRead(path) }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "canRead($mode->ADB): $path" }
                    adbOps { it.canRead(path) }
                }

                else -> false
            }

        } catch (e: IOException) {
            log(TAG, WARN) { "canRead(path=$path, mode=$mode) failed." }
            throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun file(path: LocalPath, readWrite: Boolean): FileHandle = file(path, readWrite, Mode.AUTO)

    suspend fun file(path: LocalPath, readWrite: Boolean, mode: Mode = Mode.AUTO): FileHandle = runIO {
        try {
            val file = path.asFile()
            val canNormalOpen = when (mode) {
                Mode.ROOT -> false
                Mode.ADB -> false
                else -> when {
                    readWrite -> (file.exists() && file.canWrite()) || !file.exists() && file.parentFile?.canWrite() == true
                    else -> file.isReadable()
                }
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalOpen -> {
                    log(TAG, VERBOSE) { "file($mode->NORMAL): $path" }
                    file.fileHandle(readWrite)
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "file($mode->ROOT, RW=$readWrite): $path" }
                    // We need to keep the resource alive until the caller is done with the object
                    val resource = rootManager.serviceClient.get()
                    rootOps {
                        it.file(path, readWrite).callbacks {
                            resource.close()
                            log(TAG, VERBOSE) { "file($mode->ROOT, RW=$readWrite): Closing resource for $path" }
                        }
                    }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "file($mode->ADB, RW=$readWrite): $path" }
                    // We need to keep the resource alive until the caller is done with the object
                    val resource = adbManager.serviceClient.get()
                    adbOps {
                        it.file(path, readWrite).callbacks {
                            resource.close()
                            log(TAG, VERBOSE) { "file($mode->ADB, RW=$readWrite): Closing resource for $path" }
                        }
                    }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "file(path=$path, mode=$mode, RW=$readWrite) failed." }
            if (readWrite) throw WriteException(path = path, cause = e)
            else throw ReadException(path = path, cause = e)
        }
    }

    override suspend fun delete(path: LocalPath, recursive: Boolean) = delete(
        path,
        recursive = recursive,
        mode = Mode.AUTO
    )

    suspend fun delete(
        path: LocalPath,
        recursive: Boolean = false,
        mode: Mode = Mode.AUTO
    ): Unit = runIO {
        try {
            val javaFile = path.asFile()

            // On devices without root or adb:
            // Determining whether if a file can't be deleted or just does not exist prevents WriteException errors.
            val normalCanWrite = when {
                mode == Mode.ROOT -> false
                mode == Mode.ADB -> false
                javaFile.canWrite() -> true
                // We couldn't write but it exists, so we can't write normally
                javaFile.exists() -> false
                // Does it not exist or do we lack permission? Also see `LocalGateway.exists(...)`
                else -> when {
                    // On Android 12+ Android/data isn't accessible anymore via normal java file access.
                    hasApiLevel(32) && storageEnvironment.publicDataDirs.any { it.isAncestorOf(path) } -> false
                    // If the file path is on public storage, and it wasn't Android/data then, assume true
                    else -> storageEnvironment.externalDirs
                        .firstOrNull { it.isAncestorOf(path) }
                        ?.asFile()
                        ?.canWrite() ?: false
                }
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && normalCanWrite -> {
                    log(TAG, VERBOSE) { "delete($mode->NORMAL): $path" }

                    var success = javaFile.run {
                        when {
                            Bugs.isDryRun -> {
                                log(TAG, INFO) { "DRYRUN: Not deleting $javaFile" }
                                javaFile.canWrite()
                            }

                            recursive -> deleteRecursively()
                            else -> delete()
                        }
                    }

                    if (!success) {
                        success = !javaFile.exists()
                        if (success) {
                            log(TAG, WARN) { "Tried to delete file, but it's already gone: $path" }
                        } else if (!normalCanWrite) {
                            // This was not AUTO, but Mode.NORMAL, we don't try other modes after this
                            throw WriteException(path = path)
                        }
                    }

                    if (!success) {
                        if (mode == Mode.AUTO && hasRoot()) {
                            delete(path, recursive = recursive, mode = Mode.ROOT)
                            return@runIO
                        } else {
                            throw IOException("delete() call returned false")
                        }
                    }

                    if (!success) {
                        if (mode == Mode.AUTO && hasAdb()) {
                            delete(path, recursive = recursive, mode = Mode.ADB)
                            return@runIO
                        } else {
                            throw IOException("delete() call returned false")
                        }
                    }
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "delete($mode->ROOT): $path" }
                    rootOps {
                        if (Bugs.isDryRun) log(TAG, INFO) { "DRYRUN: Not deleting (root) $javaFile" }
                        val success = it.delete(path, recursive = true, dryRun = Bugs.isDryRun)
                        if (!success) throw IOException("Root delete() call returned false")
                    }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "delete($mode->ADB): $path" }
                    adbOps {
                        if (Bugs.isDryRun) log(TAG, INFO) { "DRYRUN: Not deleting (adb) $javaFile" }
                        val success = it.delete(path, recursive = true, dryRun = Bugs.isDryRun)
                        if (!success) throw IOException("ADB delete() call returned false")
                    }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "delete(path=$path, mode=$mode) failed." }
            throw WriteException(path = path, cause = e)
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
                Mode.ADB -> false
                else -> linkPathJava.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "createSymlink($mode->NORMAL): $linkPath -> $targetPath" }
                    linkPathJava.createSymlink(targetPathJava)
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "createSymlink($mode->ROOT): $linkPath -> $targetPath" }
                    rootOps { it.createSymlink(linkPath, targetPath) }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "createSymlink($mode->ADB): $linkPath -> $targetPath" }
                    adbOps { it.createSymlink(linkPath, targetPath) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "createSymlink(linkPath=$linkPath, targetPath=$targetPath, mode=$mode) failed." }
            throw WriteException(path = linkPath, cause = e)
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
                Mode.ADB -> false
                else -> path.file.canWrite()
            }
            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "setModifiedAt($mode->NORMAL): $path" }
                    path.file.setLastModified(modifiedAt.toEpochMilli())
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "setModifiedAt($mode->ROOT): $path" }
                    rootOps { it.setModifiedAt(path, modifiedAt) }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "setModifiedAt($mode->ADB): $path" }
                    adbOps { it.setModifiedAt(path, modifiedAt) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "setModifiedAt(path=$path, modifiedAt=$modifiedAt, mode=$mode) failed." }
            throw WriteException(path = path, cause = e)
        }
    }

    override suspend fun setPermissions(path: LocalPath, permissions: Permissions): Boolean =
        setPermissions(path, permissions, Mode.AUTO)

    suspend fun setPermissions(path: LocalPath, permissions: Permissions, mode: Mode = Mode.AUTO): Boolean = runIO {
        try {
            val canNormalWrite = when (mode) {
                Mode.ROOT -> false
                Mode.ADB -> false
                else -> path.file.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "setPermissions($mode->NORMAL): $path" }
                    path.file.setPermissions(permissions)
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "setPermissions($mode->ROOT): $path" }
                    rootOps { it.setPermissions(path, permissions) }
                }


                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "setPermissions($mode->ADB): $path" }
                    adbOps { it.setPermissions(path, permissions) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "setPermissions(path=$path, permissions=${permissions}, mode=$mode) failed." }
            throw WriteException(path = path, cause = e)
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
                Mode.ADB -> false
                else -> path.file.canWrite()
            }

            when {
                mode == Mode.NORMAL || mode == Mode.AUTO && canNormalWrite -> {
                    log(TAG, VERBOSE) { "setOwnership($mode->NORMAL): $path" }
                    path.file.setOwnership(ownership)
                }

                hasRoot() && (mode == Mode.ROOT || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "setOwnership($mode->ROOT): $path" }
                    rootOps { it.setOwnership(path, ownership) }
                }

                hasAdb() && (mode == Mode.ADB || mode == Mode.AUTO) -> {
                    log(TAG, VERBOSE) { "setOwnership($mode->ADB): $path" }
                    adbOps { it.setOwnership(path, ownership) }
                }

                else -> throw IOException("No matching mode available.")
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "setOwnership(path=$path, ownership=$ownership, mode=$mode) failed." }
            throw WriteException(path = path, cause = e)
        }
    }

    enum class Mode {
        AUTO, NORMAL, ROOT, ADB
    }

    companion object {
        val TAG = logTag("Gateway", "Local")
    }
}