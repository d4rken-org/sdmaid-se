package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<AppJunkDetailsFragmentArgs>()

    init {
        appCleaner.data
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppJunkDetailsEvents>()

    val state = combine(
        appCleaner.progress,
        appCleaner.data
            .filterNotNull()
            .distinctUntilChangedBy { junks -> junks.junks.map { it.identifier }.toSet() },
    ) { progress, data ->
        State(
            items = data.junks.sortedByDescending { it.size },
            target = args.identifier,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<AppJunk>,
        val target: Installed.InstallId?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}