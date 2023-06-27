package eu.darken.sdmse.common.debug.logviewer.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.logviewer.core.LogViewLogger
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.items.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LogViewViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val logViewLogger: LogViewLogger,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val currentLog = mutableListOf<LogViewerAdapter.LogViewerRow.Item>()

    val log = logViewLogger.lines
        .map {
            currentLog.add(LogViewerAdapter.LogViewerRow.Item(it))
            if (currentLog.size > 50) currentLog.removeFirst()
            currentLog
        }
        .throttleLatest(500)
        .onStart {
            currentLog.clear()
            Logging.install(logViewLogger)
        }
        .onCompletion { Logging.remove(logViewLogger) }
        .asLiveData2()

    companion object {
        private val TAG = logTag("LogView", "ViewModel")
    }
}