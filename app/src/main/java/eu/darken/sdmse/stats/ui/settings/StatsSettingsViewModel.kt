package eu.darken.sdmse.stats.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.stats.core.StatsRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class StatsSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
) : ViewModel3(dispatcherProvider) {

    val state = statsRepo.state.map {
        State(
            databaseSize = it.databaseSize
        )
    }.asLiveData2()

    fun resetAll() = launch {
        log(TAG) { "resetAll()" }
        statsRepo.resetAll()
    }

    data class State(
        val databaseSize: Long,
    )

    companion object {
        private val TAG = logTag("Settings", "Stats", "ViewModel")
    }
}