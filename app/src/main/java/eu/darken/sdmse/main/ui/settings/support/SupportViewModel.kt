package eu.darken.sdmse.main.ui.settings.support

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.debug.recorder.ui.RecorderActivity
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    private val sdmId: SDMId,
    private val sessionManager: DebugLogSessionManager,
) : ViewModel3(dispatcherProvider) {

    sealed interface SupportEvents {
        data object ShowShortRecordingWarning : SupportEvents
        data class LaunchRecorderActivity(val intent: Intent) : SupportEvents
    }

    val clipboardEvent = SingleLiveEvent<String>()
    val events = SingleLiveEvent<SupportEvents>()

    val isRecording = sessionManager.sessions
        .map { sessions -> sessions.any { it is DebugLogSession.Recording } }
        .asLiveData2()

    data class DebugLogFolderStats(
        val sessionCount: Int = 0,
        val totalSizeBytes: Long = 0L,
    )

    val debugLogFolderStats = sessionManager.sessions
        .map { sessions ->
            DebugLogFolderStats(
                sessionCount = sessions.size,
                totalSizeBytes = sessions.sumOf { it.diskSize },
            )
        }
        .asLiveData2()

    fun copyInstallID() = launch {
        clipboardEvent.postValue(sdmId.id)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        sessionManager.startRecording()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        when (val result = sessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> {
                events.postValue(SupportEvents.ShowShortRecordingWarning)
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
        events.postValue(SupportEvents.LaunchRecorderActivity(intent))
    }

    fun refreshSessions() {
        sessionManager.refresh()
    }

    fun deleteAllDebugLogs() = launch {
        log(TAG) { "deleteAllDebugLogs()" }
        sessionManager.deleteAll()
    }

    companion object {
        private val TAG = logTag("Support", "ViewModel")
    }
}
