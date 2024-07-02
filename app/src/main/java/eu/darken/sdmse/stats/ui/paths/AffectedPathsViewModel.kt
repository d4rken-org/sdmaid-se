package eu.darken.sdmse.stats.ui.paths

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.ui.paths.elements.AffectedPathVH
import eu.darken.sdmse.stats.ui.paths.elements.AffectedPathsHeaderVH
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@HiltViewModel
class AffectedPathsViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = AffectedPathsFragmentArgs.fromSavedStateHandle(handle)

    private val reportId: ReportId
        get() = args.reportId

    private val report: Flow<Report> = flowOf(reportId)
        .map { statsRepo.getById(it) }
        .onEach { log(TAG) { "Retrieved report: $it" } }
        .filterNotNull()

    private val affectedPaths: Flow<Collection<AffectedPath>> = flowOf(reportId)
        .map { statsRepo.getAffectedPaths(it) }
        .onEach { log(TAG) { "Retrieved affected paths ${it.size} items" } }

    val state = combine(
        report,
        affectedPaths
    ) { report, affectedPaths ->
        val elements = mutableListOf<AffectedPathsAdapter.Item>()

        AffectedPathsHeaderVH.Item(
            report = report,
            affectedPaths = affectedPaths,
        ).run { elements.add(this) }

        affectedPaths
            .sortedBy { it.path.path }
            .map { path -> AffectedPathVH.Item(path) }
            .run { elements.addAll(this) }

        State(elements)
    }
        .onStart { emit(State()) }
        .asLiveData2()

    data class State(
        val elements: List<AffectedPathsAdapter.Item>? = null,
    )

    companion object {
        private val TAG = logTag("Stats", "Affected", "Paths", "ViewModel")
    }
}