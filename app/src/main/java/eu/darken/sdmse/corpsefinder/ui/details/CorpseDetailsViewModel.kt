package eu.darken.sdmse.corpsefinder.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.corpsefinder.core.hasData
import eu.darken.sdmse.corpsefinder.core.tasks.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CorpseDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    corpseFinder: CorpseFinder,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<CorpseDetailsFragmentArgs>()
    private var currentTarget: CorpseIdentifier? = null

    init {
        corpseFinder.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<CorpseDetailsEvents>()

    val state = combine(
        corpseFinder.progress,
        corpseFinder.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { data -> data.corpses.map { it.identifier }.toSet() },
    ) { progress, data ->
        State(
            items = data.corpses.toList(),
            target = currentTarget ?: args.corpsePath,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<Corpse>,
        val target: CorpseIdentifier?,
        val progress: Progress.Data?,
    )

    fun updatePage(identifier: CorpseIdentifier) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Details", "ViewModel")
    }
}