package eu.darken.sdmse.common.debug.recorder.core

import android.net.Uri
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.deleteRecursivelySafe
import eu.darken.sdmse.common.flow.replayingShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLogSessionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val debugLogZipper: DebugLogZipper,
) {

    private val fsMutex = Mutex()
    private val zippingIds = MutableStateFlow<Set<SessionId>>(emptySet())
    private val failedZipIds = MutableStateFlow<Set<SessionId>>(emptySet())
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val pendingAutoZips = java.util.Collections.synchronizedSet(mutableSetOf<SessionId>())

    val sessions: Flow<List<DebugLogSession>> = combine(
        recorderModule.state,
        zippingIds,
        failedZipIds,
        refreshTrigger.onStart { emit(Unit) },
    ) { recorderState, zipping, failedZips, _ ->
        withContext(dispatcherProvider.IO) {
            val (sessions, orphans) = fsMutex.withLock {
                val activeRecordingDir = recorderState.currentLogDir
                val logDirs = recorderModule.getLogDirectories()
                val scanned = scanSessions(logDirs, activeRecordingDir)
                val orphans = findOrphans(scanned, zipping, pendingAutoZips)
                val overlaid = applyOverlays(scanned, zipping, failedZips)
                overlaid to orphans
            }
            // Schedule auto-zips outside the lock to avoid reentrancy
            if (orphans.isNotEmpty()) {
                orphans.forEach { (sessionId, _) -> pendingAutoZips.add(sessionId) }
                appScope.launch {
                    orphans.forEach { (sessionId, logDir) ->
                        log(TAG, INFO) { "Found orphan session dir, auto-zipping: $sessionId" }
                        zipSessionAsync(sessionId, logDir)
                    }
                }
            }
            sessions
        }
    }.replayingShare(appScope)

    private fun applyOverlays(
        sessions: List<DebugLogSession>,
        zipping: Set<SessionId>,
        failedZips: Set<SessionId>,
    ): List<DebugLogSession> = sessions.map { session ->
        when {
            session is DebugLogSession.Recording -> session
            session.id in zipping -> DebugLogSession.Zipping(
                id = session.id,
                createdAt = session.createdAt,
                logDir = session.logDir,
                diskSize = session.diskSize,
            )
            session.id in failedZips -> DebugLogSession.Failed(
                id = session.id,
                createdAt = session.createdAt,
                logDir = session.logDir,
                diskSize = session.diskSize,
                reason = DebugLogSession.Failed.Reason.ZIP_FAILED,
            )
            else -> session
        }
    }

    private fun zipSessionAsync(sessionId: SessionId, logDir: File) {
        zippingIds.update { it + sessionId }
        appScope.launch(dispatcherProvider.IO) {
            try {
                fsMutex.withLock {
                    debugLogZipper.zip(logDir)
                }
                log(TAG, INFO) { "Zipping complete for $sessionId" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Zipping failed for $sessionId: ${e.asLog()}" }
                failedZipIds.update { it + sessionId }
            } finally {
                pendingAutoZips.remove(sessionId)
                zippingIds.update { it - sessionId }
                refresh()
            }
        }
    }

    suspend fun startRecording(): File {
        return recorderModule.startRecorder()
    }

    suspend fun requestStopRecording(): RecorderModule.StopResult {
        val result = recorderModule.requestStopRecorder()
        if (result is RecorderModule.StopResult.Stopped) {
            zipSessionAsync(result.sessionId, result.logDir)
        }
        return result
    }

    suspend fun forceStopRecording(): RecorderModule.StopResult.Stopped? {
        val logDir = recorderModule.stopRecorder() ?: return null
        val sessionId = SessionId.derive(logDir)
        zipSessionAsync(sessionId, logDir)
        return RecorderModule.StopResult.Stopped(sessionId = sessionId, logDir = logDir)
    }

    private fun findSessionFiles(sessionId: SessionId): Pair<File?, File?> {
        val baseName = sessionId.baseName
        for (logParent in recorderModule.getLogDirectories()) {
            val dir = File(logParent, baseName)
            val zip = File(logParent, "$baseName.zip")
            val dirExists = dir.exists() && dir.isDirectory
            val zipExists = zip.exists() && zip.isFile
            if (dirExists || zipExists) {
                return Pair(if (dirExists) dir else null, if (zipExists) zip else null)
            }
        }
        return Pair(null, null)
    }

    suspend fun zipSession(sessionId: SessionId): File = fsMutex.withLock {
        // Do NOT call sessions.first() here — it acquires fsMutex in its producer, which would deadlock.
        val activeDir = recorderModule.getCurrentLogDir()
        if (activeDir != null) {
            require(SessionId.derive(activeDir) != sessionId) { "Cannot zip an active recording session" }
        }

        val (dir, existingZip) = findSessionFiles(sessionId)
        if (existingZip != null && existingZip.length() > 0) {
            if (dir == null || existingZip.lastModified() >= dir.lastModified()) {
                return@withLock existingZip
            }
        }
        requireNotNull(dir) { "No log directory found for session $sessionId" }
        failedZipIds.update { it - sessionId }
        withContext(dispatcherProvider.IO) { debugLogZipper.zip(dir) }
    }

    suspend fun getZipUri(sessionId: SessionId): Uri {
        val zipFile = zipSession(sessionId)
        return debugLogZipper.getUriForZip(zipFile)
    }

    suspend fun delete(sessionId: SessionId) = fsMutex.withLock {
        // Check recorder state directly — do NOT call sessions.first() (deadlock risk with fsMutex)
        val activeDir = recorderModule.getCurrentLogDir()
        if (activeDir != null) {
            require(SessionId.derive(activeDir) != sessionId) { "Cannot delete an active recording session" }
        }
        require(sessionId !in zippingIds.value) {
            "Cannot delete session $sessionId while it's being zipped"
        }
        val baseName = sessionId.baseName
        withContext(dispatcherProvider.IO) {
            recorderModule.getLogDirectories().forEach { parent ->
                File(parent, baseName).let { dir ->
                    if (dir.exists()) {
                        if (dir.deleteRecursivelySafe()) {
                            log(TAG) { "Deleted session dir: ${dir.path}" }
                        }
                    }
                }
                File(parent, "$baseName.zip").let { zip ->
                    if (zip.exists()) {
                        zip.delete()
                        log(TAG) { "Deleted session zip: ${zip.path}" }
                    }
                }
            }
        }
        failedZipIds.update { it - sessionId }
        refresh()
    }

    suspend fun deleteAll() = fsMutex.withLock {
        val currentZipping = zippingIds.value
        val activeDir = recorderModule.getCurrentLogDir()
        withContext(dispatcherProvider.IO) {
            recorderModule.getLogDirectories().forEach { parent ->
                parent.listFiles()?.forEach { file ->
                    val sessionId = SessionId.derive(file)
                    if (sessionId in currentZipping) return@forEach
                    if (activeDir != null && file.isDirectory && file.absolutePath == activeDir.absolutePath) return@forEach

                    if (file.deleteRecursivelySafe()) {
                        log(TAG) { "Deleted: ${file.path}" }
                    }
                }
            }
        }
        failedZipIds.update { emptySet() }
        refresh()
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    companion object {
        private val TAG = logTag("Debug", "Log", "Session", "Manager")

        private val NEW_TIMESTAMP_REGEX = Regex("""\d{8}T\d{6}Z""")
        private val NEW_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        private val OLD_TIMESTAMP_REGEX = Regex("""\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}-\d{3}""")
        private val OLD_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS").withZone(ZoneOffset.UTC)

        fun parseCreatedAt(file: File): Instant {
            val name = file.name

            NEW_TIMESTAMP_REGEX.find(name)?.value?.let { match ->
                try {
                    return NEW_TIMESTAMP_FORMAT.parse(match, Instant::from)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to parse new timestamp from ${file.name}: ${e.message}" }
                }
            }

            OLD_TIMESTAMP_REGEX.find(name)?.value?.let { match ->
                try {
                    return OLD_TIMESTAMP_FORMAT.parse(match, Instant::from)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to parse old timestamp from ${file.name}: ${e.message}" }
                }
            }

            return try {
                val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                attrs.creationTime().toInstant()
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to read creation time for ${file.name}: ${e.message}" }
                Instant.ofEpochMilli(file.lastModified())
            }
        }

        private fun computeDiskSize(file: File): Long {
            if (!file.exists()) return 0L
            return if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
        }

        fun findOrphans(
            scannedSessions: List<DebugLogSession>,
            currentlyZipping: Set<SessionId>,
            pendingAutoZips: Set<SessionId>,
        ): List<Pair<SessionId, File>> {
            return scannedSessions.filterIsInstance<DebugLogSession.Zipping>()
                .filter { it.id !in currentlyZipping && it.id !in pendingAutoZips }
                .map { it.id to it.logDir }
        }

        fun scanSessions(
            logDirectories: List<File>,
            activeRecordingDir: File?,
        ): List<DebugLogSession> {
            val sessions = mutableListOf<DebugLogSession>()

            logDirectories.forEach { parent ->
                val children = parent.listFiles() ?: return@forEach

                // Clean up stale temp files from interrupted zip operations
                children.filter { it.extension == "tmp" && it.name.endsWith(".zip.tmp") }.forEach { tmp ->
                    tmp.delete()
                    log(TAG) { "Deleted stale temp file: ${tmp.name}" }
                }

                val dirs = children.filter { it.isDirectory }
                val zips = children.filter { it.extension == "zip" }.associateBy { it.nameWithoutExtension }

                for (dir in dirs) {
                    val sessionId = SessionId.derive(dir)
                    val createdAt = parseCreatedAt(dir)
                    val diskSize = computeDiskSize(dir)

                    when {
                        activeRecordingDir != null && dir.absolutePath == activeRecordingDir.absolutePath -> {
                            sessions.add(DebugLogSession.Recording(sessionId, createdAt, dir, diskSize))
                        }
                        zips.containsKey(dir.name) -> {
                            val zipFile = zips[dir.name]!!
                            sessions.add(classifyWithZip(sessionId, createdAt, dir, diskSize, zipFile))
                        }
                        else -> {
                            sessions.add(classifyOrphan(sessionId, createdAt, dir, diskSize))
                        }
                    }
                }

                // Standalone zips without a directory
                for ((name, zipFile) in zips) {
                    if (dirs.any { it.name == name }) continue
                    val sessionId = SessionId.derive(zipFile)
                    val createdAt = parseCreatedAt(zipFile)
                    val zipSize = zipFile.length()
                    if (zipSize > 0) {
                        sessions.add(
                            DebugLogSession.Finished(
                                id = sessionId,
                                createdAt = createdAt,
                                logDir = File(parent, name),
                                diskSize = zipSize,
                                zipFile = zipFile,
                                compressedSize = zipSize,
                            )
                        )
                    } else {
                        sessions.add(
                            DebugLogSession.Failed(
                                id = sessionId,
                                createdAt = createdAt,
                                logDir = File(parent, name),
                                diskSize = 0L,
                                reason = DebugLogSession.Failed.Reason.CORRUPT_ZIP,
                            )
                        )
                    }
                }
            }

            return sessions.sortedWith(
                compareByDescending<DebugLogSession> { it.createdAt }.thenBy { it.id.value }
            )
        }

        private fun classifyWithZip(
            sessionId: SessionId,
            createdAt: Instant,
            dir: File,
            diskSize: Long,
            zipFile: File,
        ): DebugLogSession {
            val coreLog = File(dir, "core.log")
            val zipValid = zipFile.length() > 0
            val totalDiskSize = diskSize + zipFile.length()

            return when {
                !coreLog.exists() && zipValid -> DebugLogSession.Finished(sessionId, createdAt, dir, totalDiskSize, zipFile, zipFile.length())
                !coreLog.exists() -> DebugLogSession.Failed(sessionId, createdAt, dir, diskSize, DebugLogSession.Failed.Reason.MISSING_LOG)
                coreLog.length() == 0L && zipValid -> DebugLogSession.Finished(sessionId, createdAt, dir, totalDiskSize, zipFile, zipFile.length())
                coreLog.length() == 0L -> DebugLogSession.Failed(sessionId, createdAt, dir, diskSize, DebugLogSession.Failed.Reason.EMPTY_LOG)
                zipValid -> DebugLogSession.Finished(sessionId, createdAt, dir, totalDiskSize, zipFile, zipFile.length())
                else -> DebugLogSession.Failed(sessionId, createdAt, dir, diskSize, DebugLogSession.Failed.Reason.CORRUPT_ZIP)
            }
        }

        private fun classifyOrphan(
            sessionId: SessionId,
            createdAt: Instant,
            dir: File,
            diskSize: Long,
        ): DebugLogSession {
            val coreLog = File(dir, "core.log")
            return when {
                !coreLog.exists() -> {
                    log(TAG, WARN) { "Orphan session dir has no core.log: $sessionId" }
                    DebugLogSession.Failed(sessionId, createdAt, dir, diskSize, DebugLogSession.Failed.Reason.MISSING_LOG)
                }
                coreLog.length() == 0L -> {
                    log(TAG, WARN) { "Orphan session dir has empty core.log: $sessionId" }
                    DebugLogSession.Failed(sessionId, createdAt, dir, diskSize, DebugLogSession.Failed.Reason.EMPTY_LOG)
                }
                else -> {
                    // Return as Zipping — findOrphans() will trigger auto-zip
                    DebugLogSession.Zipping(sessionId, createdAt, dir, diskSize)
                }
            }
        }
    }
}
