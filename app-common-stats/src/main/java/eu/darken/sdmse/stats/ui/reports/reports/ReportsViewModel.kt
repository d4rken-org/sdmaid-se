package eu.darken.sdmse.stats.ui.reports

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.intervalFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.ui.AffectedFilesRoute
import eu.darken.sdmse.stats.ui.AffectedPkgsRoute
import eu.darken.sdmse.stats.ui.SpaceHistoryRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class ReportsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private val nowTicks = intervalFlow(1.minutes).map { Instant.now() }

    val state: StateFlow<State> = combine(
        statsRepo.reports,
        nowTicks,
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { reports, now, isPro ->
        State(
            rows = reports.reversed().map { it.toRow() },
            isPro = isPro,
            now = now,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State(rows = emptyList()) },
    )

    fun onReportClick(row: Row) {
        log(TAG) { "onReportClick($row)" }
        when (row.status) {
            Report.Status.SUCCESS, Report.Status.PARTIAL_SUCCESS -> when (row.tool) {
                SDMTool.Type.APPCONTROL -> navTo(AffectedPkgsRoute(reportId = row.reportId.toString()))
                else -> navTo(AffectedFilesRoute(reportId = row.reportId.toString()))
            }

            Report.Status.FAILURE -> {
                row.errorMessage?.let { events.tryEmit(Event.ShowError(it)) }
            }
        }
    }

    fun onStorageTrendClick() = launch {
        log(TAG) { "onStorageTrendClick()" }
        if (upgradeRepo.isPro()) {
            navTo(SpaceHistoryRoute())
        } else {
            navTo(UpgradeRoute(forced = true))
        }
    }

    private fun Report.toRow() = Row(
        reportId = reportId,
        tool = tool,
        status = status,
        endAt = endAt,
        primaryMessage = primaryMessage,
        secondaryMessage = secondaryMessage,
        errorMessage = errorMessage,
    )

    data class State(
        val rows: List<Row>? = null,
        val isPro: Boolean = false,
        val now: Instant = Instant.EPOCH,
    )

    data class Row(
        val reportId: ReportId,
        val tool: SDMTool.Type,
        val status: Report.Status,
        val endAt: Instant,
        val primaryMessage: String?,
        val secondaryMessage: String?,
        val errorMessage: String?,
    )

    sealed interface Event {
        data class ShowError(val msg: String) : Event
    }

    companion object {
        private val TAG = logTag("Stats", "Reports", "ViewModel")
    }
}
