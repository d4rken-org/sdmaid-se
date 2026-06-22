package eu.darken.sdmse.main.ui.settings.support.sessions

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DebugLogSessionsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val sessionManager: DebugLogSessionManager,
) : ViewModel4(dispatcherProvider, TAG) {

    data class State(
        val sessions: List<DebugLogSession> = emptyList(),
    ) {
        val hasDeletable: Boolean = sessions.any {
            it is DebugLogSession.Finished || it is DebugLogSession.Failed
        }
    }

    sealed interface Event {
        data class LaunchRecorder(val sessionId: SessionId) : Event
    }

    val state: StateFlow<State> = sessionManager.sessions
        .map { State(sessions = it) }
        .safeStateIn(State()) { State() }

    val events = SingleEventFlow<Event>()

    fun openSession(sessionId: SessionId) {
        events.tryEmit(Event.LaunchRecorder(sessionId))
    }

    fun delete(sessionId: SessionId) = launch {
        sessionManager.delete(sessionId)
    }

    fun deleteAll() = launch {
        sessionManager.deleteAll()
    }

    fun stopRecording() = launch {
        sessionManager.forceStopRecording()
    }

    fun refresh() {
        sessionManager.refresh()
    }

    companion object {
        private val TAG = logTag("Debug", "Log", "Sessions", "ViewModel")
    }
}
