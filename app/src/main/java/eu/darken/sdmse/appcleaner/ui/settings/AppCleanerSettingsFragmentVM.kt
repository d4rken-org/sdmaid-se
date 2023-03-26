package eu.darken.sdmse.appcleaner.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class AppCleanerSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val rootManager: RootManager,
) : ViewModel3(dispatcherProvider) {


    val state = flow {
        emit(
            State(
                isRooted = rootManager.useRoot()
            )
        )
    }.asLiveData2()

    data class State(
        val isRooted: Boolean
    )

    companion object {
        private val TAG = logTag("Settings", "AppCleaner", "VM")
    }
}