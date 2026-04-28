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
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.theming.ThemeState
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
    private val sessionManager: DebugLogSessionManager,
    generalSettings: GeneralSettings,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val sessionId = SessionId(
        handle.get<String>(RecorderActivity.EXTRA_SESSION_ID)
            ?: throw IllegalStateException("No session ID provided")
    )

    private val sessionFlow = sessionManager.sessions
        .map { sessions -> sessions.find { it.id == sessionId } }

    val themeState: StateFlow<ThemeState> = combine(
        generalSettings.themeMode.flow,
        generalSettings.themeStyle.flow,
    ) { mode, style ->
        ThemeState(mode = mode, style = style)
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), ThemeState())

    val state: StateFlow<State> = sessionFlow
        .mapLatest { session ->
            if (session == null) State()
            else withContext(dispatcherProvider.IO) { buildState(session) }
        }
        .safeStateIn(initialValue = State(), onError = { State() })

    val events = SingleEventFlow<Event>()

    init {
        sessionFlow
            .filter { it == null }
            .take(1)
            .onEach { events.emit(Event.Close) }
            .launchIn(vmScope)
    }

    private fun buildState(session: DebugLogSession): State {
        val logDir = session.logDir

        val logEntries: List<LogFileEntry> = try {
            if (logDir.isDirectory) {
                logDir.listFiles()
                    ?.map { LogFileEntry(path = it, size = it.length()) }
                    ?.sortedByDescending { it.size }
                    ?: emptyList()
            } else emptyList()
        } catch (e: IOException) {
            log(TAG, WARN) { "Failed to list log files for $logDir: $e" }
            emptyList()
        } catch (e: SecurityException) {
            log(TAG, WARN) { "SecurityException listing log files for $logDir: $e" }
            emptyList()
        }

        val recordingDuration: Duration? = try {
            if (logDir.isDirectory) {
                val attrs = Files.readAttributes(logDir.toPath(), BasicFileAttributes::class.java)
                val coreLog = File(logDir, "core.log")
                val endTime = if (coreLog.exists()) coreLog.lastModified() else logDir.lastModified()
                Duration.between(attrs.creationTime().toInstant(), Instant.ofEpochMilli(endTime))
            } else null
        } catch (e: IOException) {
            log(TAG, WARN) { "Failed to read recording duration: $e" }
            null
        } catch (e: SecurityException) {
            log(TAG, WARN) { "SecurityException reading recording duration: $e" }
            null
        }

        return State(
            logDir = logDir,
            logEntries = logEntries,
            recordingDuration = recordingDuration,
            isZipping = session is DebugLogSession.Zipping,
            isFailed = session is DebugLogSession.Failed,
            failedReason = (session as? DebugLogSession.Failed)?.reason,
            compressedFile = (session as? DebugLogSession.Finished)?.zipFile,
            compressedSize = (session as? DebugLogSession.Finished)?.compressedSize,
        )
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
                "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION}",
            )
            putExtra(Intent.EXTRA_TEXT, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.debug_debuglog_file_label))
        events.emit(Event.LaunchShare(chooser))
    }

    fun goPrivacyPolicy() {
        webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
    }

    fun close() = launch {
        events.emit(Event.Close)
    }

    fun delete() = launch {
        sessionManager.delete(sessionId)
        events.emit(Event.Close)
    }

    fun onShareLaunchFailed(error: Throwable) {
        log(TAG, WARN) { "onShareLaunchFailed: $error" }
        errorEvents.tryEmit(error)
    }

    sealed interface Event {
        data class LaunchShare(val intent: Intent) : Event
        data object Close : Event
    }

    data class State(
        val logDir: File? = null,
        val logEntries: List<LogFileEntry> = emptyList(),
        val compressedFile: File? = null,
        val compressedSize: Long? = null,
        val recordingDuration: Duration? = null,
        val isZipping: Boolean = false,
        val isFailed: Boolean = false,
        val failedReason: DebugLogSession.Failed.Reason? = null,
    )

    data class LogFileEntry(
        val path: File,
        val size: Long?,
    )

    companion object {
        private val TAG = logTag("Debug", "Recorder", "ViewModel")
    }
}
