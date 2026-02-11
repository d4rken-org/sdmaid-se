package eu.darken.sdmse.stats.ui.reports

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.StatsRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val updateTicks = intervalFlow(1.minutes)
    val event = SingleLiveEvent<ReportsEvent>()

    val items = combine(
        statsRepo.reports,
        updateTicks,
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { reports, tick, isPro ->
        val items = mutableListOf<ReportsAdapter.Item>()

        reports.reversed().map { report ->
            ReportBaseRowVH.Item(
                tick = tick,
                report = report,
                onReportAction = {
                    when (report.status) {
                        Report.Status.SUCCESS, Report.Status.PARTIAL_SUCCESS -> {
                            when (report.tool) {
                                SDMTool.Type.APPCONTROL -> ReportsFragmentDirections.actionReportsFragmentToAffectedPkgsFragment(
                                    report.reportId
                                )

                                else -> ReportsFragmentDirections.actionReportsFragmentToAffectedFilesFragment(
                                    report.reportId
                                )
                            }.navigate()
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
            listItems = items,
            isPro = isPro,
        )
    }
        .onStart { emit(State()) }
        .asLiveData()

    fun openStorageTrend() = launch {
        log(TAG) { "openStorageTrend()" }
        if (upgradeRepo.isPro()) {
            ReportsFragmentDirections.actionReportsFragmentToSpaceHistoryFragment().navigate()
        } else {
            MainDirections.goToUpgradeFragment().navigate()
        }
    }

    data class State(
        val listItems: List<ReportsAdapter.Item>? = null,
        val isPro: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Stats", "Reports", "ViewModel")
    }
}
