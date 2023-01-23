package eu.darken.sdmse.corpsefinder.ui.details.item

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val corpseFinder: CorpseFinder,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = CorpseFragmentArgs.fromSavedStateHandle(handle)

    val info = corpseFinder.data
        .filterNotNull()
        .map { data ->
            data.corpses.singleOrNull { it.path == args.identifier }
        }
        .filterNotNull()
        .setupCommonEventHandlers(TAG) { "info" }
        .asLiveData2()

    data class Info(
        val corpse: Corpse,
    )

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "Fragment", "VM")
    }
}