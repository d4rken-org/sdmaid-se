package eu.darken.sdmse.appcleaner.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class AppCleanerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider) {

    val state = appCleaner.state.asLiveData2()

    companion object {
        private val TAG = logTag("Settings", "AppCleaner", "ViewModel")
    }
}