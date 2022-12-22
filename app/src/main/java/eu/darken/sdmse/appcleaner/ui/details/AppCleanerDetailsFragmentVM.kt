package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppCleanerDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}