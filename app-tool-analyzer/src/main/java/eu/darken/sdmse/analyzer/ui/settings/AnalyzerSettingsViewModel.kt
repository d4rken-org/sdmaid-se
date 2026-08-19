package eu.darken.sdmse.analyzer.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.stats.core.LowStorage
import eu.darken.sdmse.stats.core.SpaceTracker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject


@HiltViewModel
class AnalyzerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: AnalyzerSettings,
    private val spaceTracker: SpaceTracker,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    // A single reading is enough: capacity only informs the "currently X" hint next to Automatic.
    // readPrimaryStorage() can return a non-null 0/0 reading, which would render "currently 0 B".
    private val primaryCapacity = flow {
        emit(spaceTracker.readPrimaryStorage()?.spaceCapacity?.takeIf { it > 0L })
    }

    val state: StateFlow<State> = combine(
        settings.lowStorageThresholdBytes.flow,
        primaryCapacity,
    ) { custom, capacity ->
        State(
            customThresholdBytes = custom,
            primaryCapacityBytes = capacity,
            effectiveThresholdBytes = capacity?.let { LowStorage.resolveThreshold(it, custom) },
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setThreshold(bytes: Long?) = launch {
        log(TAG) { "setThreshold($bytes)" }
        settings.lowStorageThresholdBytes.value(bytes)
    }

    data class State(
        val customThresholdBytes: Long? = null,
        val primaryCapacityBytes: Long? = null,
        val effectiveThresholdBytes: Long? = null,
    )

    companion object {
        private val TAG = logTag("Settings", "Analyzer", "ViewModel")
    }
}
