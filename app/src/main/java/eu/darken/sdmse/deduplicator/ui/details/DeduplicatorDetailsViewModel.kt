package eu.darken.sdmse.deduplicator.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.corpsefinder.core.tasks.*
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.hasData
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DeduplicatorDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    deduplicator: Deduplicator,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {
    private val args by handle.navArgs<DeduplicatorDetailsFragmentArgs>()
    private var currentTarget: Duplicate.Cluster.Id? = null

    init {
        deduplicator.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<DeduplicatorDetailsEvents>()

    val state = combine(
        deduplicator.progress,
        deduplicator.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { data -> data.clusters.map { it.identifier }.toSet() },
    ) { progress, data ->
        val sortedClusters = data.clusters
            .sortedByDescending { it.averageSize }
        State(
            items = sortedClusters,
            target = currentTarget ?: args.identifier,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<Duplicate.Cluster>,
        val target: Duplicate.Cluster.Id?,
        val progress: Progress.Data?,
    )

    fun updatePage(identifier: Duplicate.Cluster.Id) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Details", "ViewModel")
    }
}