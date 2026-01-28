package eu.darken.sdmse.compressor.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.compressor.core.history.CompressionHistoryDatabase
import kotlinx.coroutines.flow.combine
import javax.inject.Inject


@HiltViewModel
class CompressorSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val historyDatabase: CompressionHistoryDatabase,
) : ViewModel3(dispatcherProvider) {

    val state = combine(
        historyDatabase.count,
        historyDatabase.databaseSize,
    ) { historyCount, databaseSize ->
        State(
            historyCount = historyCount,
            historyDatabaseSize = databaseSize,
        )
    }.asLiveData2()

    data class State(
        val historyCount: Int = 0,
        val historyDatabaseSize: Long = 0L,
    )

    fun clearHistory() = launch {
        log(TAG) { "clearHistory()" }
        historyDatabase.clear()
    }

    companion object {
        private val TAG = logTag("Settings", "Compressor", "ViewModel")
    }
}
