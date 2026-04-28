package eu.darken.sdmse.stats.ui.paths

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.StatsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AffectedPathsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val reportIdFlow = MutableStateFlow<UUID?>(null)

    fun setReportId(id: UUID) {
        if (reportIdFlow.value == id) return
        log(TAG) { "setReportId($id)" }
        reportIdFlow.value = id
    }

    val state: StateFlow<State> = reportIdFlow
        .filterNotNull()
        .flatMapLatest { id ->
            flow {
                val report = statsRepo.getById(id)
                if (report == null) {
                    log(TAG, WARN) { "Report $id not found" }
                    emit(State.NotFound)
                    return@flow
                }
                val affected = statsRepo.getAffectedPaths(id)
                val rows = affected
                    .sortedBy { it.path.path }
                    .map { Row(it) }
                emit(State.Ready(report, rows))
            }
        }
        .safeStateIn(
            initialValue = State.Loading,
            onError = { State.NotFound },
        )

    sealed interface State {
        data object Loading : State
        data object NotFound : State
        data class Ready(val report: Report, val rows: List<Row>) : State
    }

    data class Row(val affectedPath: AffectedPath)

    companion object {
        private val TAG = logTag("Stats", "Affected", "Paths", "ViewModel")
    }
}
