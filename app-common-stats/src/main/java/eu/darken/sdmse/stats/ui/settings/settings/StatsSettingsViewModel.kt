package eu.darken.sdmse.stats.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.ui.ReportsRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class StatsSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
    private val settings: StatsSettings,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val state: StateFlow<State> = kotlinx.coroutines.flow.combine(
        statsRepo.state,
        upgradeRepo.upgradeInfo.map { it.isPro },
        settings.retentionReports.flow,
        settings.retentionPaths.flow,
    ) { repoState, isPro, retentionReports, retentionPaths ->
        State(
            reportsCount = repoState.reportsCount,
            totalSpaceFreed = repoState.totalSpaceFreed,
            itemsProcessed = repoState.itemsProcessed,
            retentionReports = retentionReports,
            retentionPaths = retentionPaths,
            isPro = isPro,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onViewStatsClick() {
        if (state.value.isPro) {
            navTo(ReportsRoute)
        } else {
            navTo(UpgradeRoute(forced = true))
        }
    }

    fun setRetentionReports(age: Duration) = launch { settings.retentionReports.value(age) }
    fun resetRetentionReports() = launch { settings.retentionReports.value(StatsSettings.DEFAULT_RETENTION_REPORTS) }

    fun setRetentionPaths(age: Duration) = launch { settings.retentionPaths.value(age) }
    fun resetRetentionPaths() = launch { settings.retentionPaths.value(StatsSettings.DEFAULT_RETENTION_PATHS) }

    fun resetAll() = launch {
        log(TAG) { "resetAll()" }
        statsRepo.resetAll()
    }

    data class State(
        val reportsCount: Int = 0,
        val totalSpaceFreed: Long = 0L,
        val itemsProcessed: Long = 0L,
        val retentionReports: Duration = StatsSettings.DEFAULT_RETENTION_REPORTS,
        val retentionPaths: Duration = StatsSettings.DEFAULT_RETENTION_PATHS,
        val isPro: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "Stats", "ViewModel")
    }
}
