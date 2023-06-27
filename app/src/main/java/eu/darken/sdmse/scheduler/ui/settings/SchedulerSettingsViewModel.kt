package eu.darken.sdmse.scheduler.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class SchedulerSettingsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel3(dispatcherProvider) {

    companion object {
        private val TAG = logTag("Scheduler", "Settings", "ViewModel")
    }
}