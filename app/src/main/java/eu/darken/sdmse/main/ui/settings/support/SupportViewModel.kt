package eu.darken.sdmse.main.ui.settings.support

import android.content.Context
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.debug.recorder.ui.RecorderActivity
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    private val sdmId: SDMId,
    private val sessionManager: DebugLogSessionManager,
    private val webpageTool: WebpageTool,
    private val clipboardHelper: ClipboardHelper,
) : ViewModel4(dispatcherProvider, TAG) {

    sealed interface SupportEvents {
        data object ShowShortRecordingWarning : SupportEvents
        data class LaunchRecorderActivity(val intent: Intent) : SupportEvents
        data class ShowInstallId(val installId: String) : SupportEvents
    }

    val events = SingleEventFlow<SupportEvents>()

    val isRecording: Flow<Boolean> = sessionManager.sessions
        .map { sessions -> sessions.any { it is DebugLogSession.Recording } }

    data class DebugLogFolderStats(
        val sessionCount: Int = 0,
        val totalSizeBytes: Long = 0L,
    )

    val debugLogFolderStats: Flow<DebugLogFolderStats> = sessionManager.sessions
        .map { sessions ->
            DebugLogFolderStats(
                sessionCount = sessions.size,
                totalSizeBytes = sessions.sumOf { it.diskSize },
            )
        }

    fun copyInstallID() = launch {
        events.emit(SupportEvents.ShowInstallId(sdmId.id))
    }

    fun copyToClipboard(text: String) {
        clipboardHelper.copyToClipboard(text)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        sessionManager.startRecording()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        when (val result = sessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> {
                events.emit(SupportEvents.ShowShortRecordingWarning)
            }
            is RecorderModule.StopResult.Stopped -> {
                launchRecorderActivity(result.sessionId)
            }
            is RecorderModule.StopResult.NotRecording -> {}
        }
    }

    fun confirmStopDebugLog() = launch {
        log(TAG) { "confirmStopDebugLog()" }
        val result = sessionManager.forceStopRecording() ?: return@launch
        launchRecorderActivity(result.sessionId)
    }

    private fun launchRecorderActivity(sessionId: SessionId) {
        val intent = RecorderActivity.getLaunchIntent(context, sessionId).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        events.tryEmit(SupportEvents.LaunchRecorderActivity(intent))
    }

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun refreshSessions() {
        sessionManager.refresh()
    }

    companion object {
        private val TAG = logTag("Support", "ViewModel")
    }
}
