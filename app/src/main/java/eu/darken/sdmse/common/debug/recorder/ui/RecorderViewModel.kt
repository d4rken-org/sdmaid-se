package eu.darken.sdmse.common.debug.recorder.ui


import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.map
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
    private val sessionManager: DebugLogSessionManager,
) : ViewModel3(dispatcherProvider) {

    private val sessionId = SessionId(
        handle.get<String>(RecorderActivity.EXTRA_SESSION_ID)
            ?: throw IllegalStateException("No session ID provided")
    )

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.asLiveData2()

    val shareEvent = SingleLiveEvent<Intent>()

    init {
        launch {
            sessionManager.sessions
                .map { sessions -> sessions.find { it.id == sessionId } }
                .collect { session ->
                    if (session == null) {
                        log(TAG, WARN) { "Session $sessionId no longer exists" }
                        popNavStack()
                        return@collect
                    }

                    val logDir = session.logDir
                    val logEntries = if (logDir.isDirectory) {
                        logDir.listFiles()
                            ?.map { LogFileAdapter.Entry.Item(path = it, size = it.length()) }
                            ?.sortedByDescending { it.size }
                            ?: emptyList()
                    } else {
                        emptyList()
                    }

                    val recordingDuration = if (logDir.isDirectory) {
                        try {
                            val attrs = Files.readAttributes(logDir.toPath(), BasicFileAttributes::class.java)
                            val coreLog = File(logDir, "core.log")
                            val endTime = if (coreLog.exists()) coreLog.lastModified() else logDir.lastModified()
                            Duration.between(attrs.creationTime().toInstant(), Instant.ofEpochMilli(endTime))
                        } catch (e: Exception) {
                            log(TAG, WARN) { "Failed to read recording duration: $e" }
                            null
                        }
                    } else null

                    val failedReason = (session as? DebugLogSession.Failed)?.reason

                    stater.updateBlocking {
                        copy(
                            logDir = logDir,
                            logEntries = logEntries,
                            recordingDuration = recordingDuration,
                            isZipping = session is DebugLogSession.Zipping,
                            isFailed = session is DebugLogSession.Failed,
                            failedReason = failedReason,
                            compressedFile = (session as? DebugLogSession.Finished)?.zipFile,
                            compressedSize = (session as? DebugLogSession.Finished)?.compressedSize,
                        )
                    }
                }
        }
    }

    fun share() = launch {
        val uri = sessionManager.getZipUri(sessionId)

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "application/zip"

            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION}"
            )
            putExtra(Intent.EXTRA_TEXT, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_debuglog_file_label))
        shareEvent.postValue(chooserIntent)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
    }

    fun close() = launch {
        popNavStack()
    }

    fun delete() = launch {
        sessionManager.delete(sessionId)
        popNavStack()
    }

    data class State(
        val logDir: File? = null,
        val logEntries: List<LogFileAdapter.Entry.Item> = emptyList(),
        val compressedFile: File? = null,
        val compressedSize: Long? = null,
        val recordingDuration: Duration? = null,
        val isZipping: Boolean = false,
        val isFailed: Boolean = false,
        val failedReason: DebugLogSession.Failed.Reason? = null,
    )

    companion object {
        private val TAG = logTag("Debug", "Recorder", "ViewModel")
    }
}
