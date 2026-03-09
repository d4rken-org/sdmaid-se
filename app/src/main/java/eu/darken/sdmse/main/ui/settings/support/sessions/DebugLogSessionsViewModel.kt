package eu.darken.sdmse.main.ui.settings.support.sessions

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DebugLogSessionsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val sessionManager: DebugLogSessionManager,
) : ViewModel3(dispatcherProvider) {

    val state = sessionManager.sessions
        .map { sessions ->
            State(sessions = sessions)
        }
        .asLiveData2()

    data class State(
        val sessions: List<DebugLogSession> = emptyList(),
    )

    fun delete(sessionId: SessionId) = launch {
        sessionManager.delete(sessionId)
    }

    fun deleteAll() = launch {
        sessionManager.deleteAll()
    }

    fun stopRecording() = launch {
        sessionManager.forceStopRecording()
    }

    companion object {
        private val TAG = logTag("Debug", "Log", "Sessions", "ViewModel")
    }
}
