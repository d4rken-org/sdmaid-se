package eu.darken.sdmse.corpsefinder.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseDetailsFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args by handle.navArgs<CorpseDetailsFragmentArgs>()

    init {
        corpseFinder.data
            .filter { it == null }
            .take(1)
            .onEach {
                popNavStack()
            }
            .launchInViewModel()
    }

    val state = corpseFinder.data
        .filterNotNull()
        .map {
            State(
                items = it.corpses.toList(),
                target = args.corpsePath,
            )
        }
        .asLiveData2()

    data class State(
        val items: List<Corpse>,
        val target: APath?
    )

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "Fragment", "VM")
    }
}