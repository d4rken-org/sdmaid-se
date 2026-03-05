package eu.darken.sdmse.common.debug.recorder.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLogSessionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val debugLogZipper: DebugLogZipper,
) {

    private val zippingIds = MutableStateFlow<Set<String>>(emptySet())
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val sessions: Flow<List<DebugLogSession>> = combine(
        recorderModule.state,
        zippingIds,
        refreshTrigger.onStart { emit(Unit) },
    ) { recorderState, zipping, _ ->
        buildSessionList(recorderState, zipping)
    }.replayingShare(appScope)

    private suspend fun buildSessionList(
        recorderState: RecorderModule.State,
        zipping: Set<String>,
    ): List<DebugLogSession> = withContext(dispatcherProvider.IO) {
        val sessions = mutableListOf<DebugLogSession>()
        val activeRecordingDir = recorderState.currentLogDir

        recorderModule.getLogDirectories().forEach { parent ->
            val children = parent.listFiles() ?: return@forEach

            // Group: directories are session dirs, zips are siblings
            val dirs = children.filter { it.isDirectory }
            val zips = children.filter { it.extension == "zip" }.associateBy { it.nameWithoutExtension }

            for (dir in dirs) {
                val sessionId = dir.name
                val createdAt = getCreatedAt(dir)

                when {
                    // Active recording
                    activeRecordingDir != null && dir.absolutePath == activeRecordingDir.absolutePath -> {
                        sessions.add(DebugLogSession.Recording(sessionId, createdAt, dir))
                    }
                    // Has a zip sibling → Finished
                    zips.containsKey(sessionId) -> {
                        val zipFile = zips[sessionId]!!
                        sessions.add(
                            DebugLogSession.Finished(
                                id = sessionId,
                                createdAt = createdAt,
                                logDir = dir,
                                zipFile = zipFile,
                                compressedSize = zipFile.length(),
                            )
                        )
                    }
                    // Currently being zipped
                    sessionId in zipping -> {
                        sessions.add(DebugLogSession.Zipping(sessionId, createdAt, dir))
                    }
                    // Orphan: dir exists without zip and not recording → auto-zip
                    else -> {
                        sessions.add(DebugLogSession.Zipping(sessionId, createdAt, dir))
                        if (sessionId !in zipping) {
                            log(TAG, INFO) { "Found orphan session dir, auto-zipping: $sessionId" }
                            zipSessionAsync(sessionId, dir)
                        }
                    }
                }
            }

            // Include zips without a directory (dir was already cleaned up)
            for ((name, zipFile) in zips) {
                if (dirs.any { it.name == name }) continue
                val createdAt = getCreatedAt(zipFile)
                sessions.add(
                    DebugLogSession.Finished(
                        id = name,
                        createdAt = createdAt,
                        logDir = File(parent, name), // dir may not exist anymore
                        zipFile = zipFile,
                        compressedSize = zipFile.length(),
                    )
                )
            }
        }

        sessions.sortedByDescending { it.createdAt }
    }

    private fun getCreatedAt(file: File): Instant = try {
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        attrs.creationTime().toInstant()
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to read creation time for ${file.name}: ${e.message}" }
        Instant.ofEpochMilli(file.lastModified())
    }

    private fun zipSessionAsync(sessionId: String, logDir: File) {
        zippingIds.update { it + sessionId }
        appScope.launch(dispatcherProvider.IO) {
            try {
                debugLogZipper.zipAndGetUri(logDir)
                log(TAG, INFO) { "Zipping complete for $sessionId" }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Zipping failed for $sessionId: ${e.asLog()}" }
            } finally {
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
            zipSessionAsync(result.logDir.name, result.logDir)
        }
        return result
    }

    suspend fun forceStopRecording(): File? {
        val logDir = recorderModule.stopRecorder() ?: return null
        zipSessionAsync(logDir.name, logDir)
        return logDir
    }

    fun delete(sessionId: String) {
        if (sessionId in zippingIds.value) {
            log(TAG, WARN) { "Cannot delete session $sessionId while it's being zipped" }
            return
        }

        appScope.launch(dispatcherProvider.IO) {
            recorderModule.getLogDirectories().forEach { parent ->
                File(parent, sessionId).let { dir ->
                    if (dir.exists()) {
                        dir.deleteRecursively()
                        log(TAG) { "Deleted session dir: ${dir.path}" }
                    }
                }
                File(parent, "$sessionId.zip").let { zip ->
                    if (zip.exists()) {
                        zip.delete()
                        log(TAG) { "Deleted session zip: ${zip.path}" }
                    }
                }
            }
            refresh()
        }
    }

    fun deleteAll() {
        val currentZipping = zippingIds.value
        appScope.launch(dispatcherProvider.IO) {
            val activeDir = recorderModule.getCurrentLogDir()
            recorderModule.getLogDirectories().forEach { parent ->
                parent.listFiles()?.forEach { file ->
                    val sessionId = if (file.isDirectory) file.name else file.nameWithoutExtension
                    if (sessionId in currentZipping) return@forEach
                    if (activeDir != null && file.isDirectory && file.absolutePath == activeDir.absolutePath) return@forEach

                    file.deleteRecursively()
                    log(TAG) { "Deleted: ${file.path}" }
                }
            }
            refresh()
        }
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    companion object {
        private val TAG = logTag("Debug", "Log", "Session", "Manager")
    }
}
