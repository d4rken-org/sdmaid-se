package eu.darken.sdmse.deduplicator.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject


@HiltViewModel
class DeduplicatorSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    deduplicator: Deduplicator,
    private val settings: DeduplicatorSettings,
) : ViewModel3(dispatcherProvider) {

    val state = combine(
        deduplicator.state,
        upgradeRepo.upgradeInfo.map { it.isPro },
        settings.scanPaths.flow,
        settings.keepPreferPaths.flow,
    ) { state, isPro, scanPaths, keepPreferPaths ->
        State(
            isPro = isPro,
            state = state,
            scanPaths = scanPaths.paths.sortedBy { it.path },
            keepPreferPaths = keepPreferPaths.paths.sortedBy { it.path },
        )
    }.asLiveData2()

    data class State(
        val state: Deduplicator.State,
        val isPro: Boolean,
        val scanPaths: List<APath>,
        val keepPreferPaths: List<APath>,
    )

    fun resetScanPaths() = launch {
        log(TAG) { "resetScanPaths()" }
        settings.scanPaths.value(DeduplicatorSettings.ScanPaths())
    }

    fun resetKeepPreferPaths() = launch {
        log(TAG) { "resetKeepPreferPaths()" }
        settings.keepPreferPaths.value(DeduplicatorSettings.KeepPreferPaths())
    }

    companion object {
        private val TAG = logTag("Settings", "Deduplicator", "ViewModel")
    }
}