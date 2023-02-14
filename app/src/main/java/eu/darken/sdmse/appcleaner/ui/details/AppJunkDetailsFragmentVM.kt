package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<AppJunkDetailsFragmentArgs>()

    init {
        appCleaner.data
            .filter { it == null }
            .take(1)
            .onEach {
                popNavStack()
            }
            .launchInViewModel()
    }

    val state = appCleaner.data
        .filterNotNull()
        .map {
            State(
                items = it.junks.toList(),
                target = args.pkgId,
            )
        }
        .asLiveData2()

    data class State(
        val items: List<AppJunk>,
        val target: Pkg.Id?
    )

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}