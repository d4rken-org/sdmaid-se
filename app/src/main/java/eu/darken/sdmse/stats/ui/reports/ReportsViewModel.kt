package eu.darken.sdmse.stats.ui.reports

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.StatsRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val updateTicks = intervalFlow(1.minutes)
    val event = SingleLiveEvent<ReportsEvent>()

    val items = combine(
        statsRepo.reports,
        updateTicks,
    ) { reports, tick ->
        val items = mutableListOf<ReportsAdapter.Item>()

        reports.reversed().map { report ->
            ReportBaseRowVH.Item(
                tick = tick,
                report = report,
                onReportAction = {
                    when (report.status) {
                        Report.Status.SUCCESS, Report.Status.PARTIAL_SUCCESS -> {

                        }

                        Report.Status.FAILURE -> {
                            report.errorMessage?.let {
                                event.postValue(ReportsEvent.ShowError(it))
                            }
                        }
                    }
                }
            ).also { items.add(it) }
        }

        State(
            listItems = items
        )
    }
        .onStart { emit(State()) }
        .asLiveData()

    data class State(
        val listItems: List<ReportsAdapter.Item>? = null,
    )

    companion object {
        private val TAG = logTag("Stats", "Reports", "ViewModel")
    }
}