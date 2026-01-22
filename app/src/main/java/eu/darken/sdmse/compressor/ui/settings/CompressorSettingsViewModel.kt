package eu.darken.sdmse.compressor.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.compressor.core.Compressor
import eu.darken.sdmse.compressor.core.CompressorSettings
import eu.darken.sdmse.compressor.core.history.CompressionHistoryDatabase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject


@HiltViewModel
class CompressorSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    compressor: Compressor,
    private val settings: CompressorSettings,
    private val historyDatabase: CompressionHistoryDatabase,
) : ViewModel3(dispatcherProvider) {

    val state = combine(
        compressor.state,
        upgradeRepo.upgradeInfo.map { it.isPro },
        settings.scanPaths.flow,
        historyDatabase.count,
        historyDatabase.databaseSize,
    ) { state, isPro, scanPaths, historyCount, databaseSize ->
        State(
            isPro = isPro,
            state = state,
            scanPaths = scanPaths.paths.sortedBy { it.path },
            historyCount = historyCount,
            historyDatabaseSize = databaseSize,
        )
    }.asLiveData2()

    data class State(
        val state: Compressor.State,
        val isPro: Boolean,
        val scanPaths: List<APath>,
        val historyCount: Int = 0,
        val historyDatabaseSize: Long = 0L,
    )

    fun resetScanPaths() = launch {
        log(TAG) { "resetScanPaths()" }
        settings.scanPaths.value(CompressorSettings.ScanPaths())
    }

    fun clearHistory() = launch {
        log(TAG) { "clearHistory()" }
        historyDatabase.clear()
    }

    companion object {
        private val TAG = logTag("Settings", "Compressor", "ViewModel")
    }
}
